/* Copyright (c) 2017 Stash
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.bitcoinj.wallet.bip47;


import com.google.common.base.Joiner;
import com.google.common.net.InetAddresses;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import com.subgraph.orchid.data.exitpolicy.Network;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.bitcoinj.core.*;
import org.bitcoinj.core.listeners.OnTransactionBroadcastListener;
import org.bitcoinj.signers.TransactionSigner;
import org.bitcoinj.wallet.*;
import org.bitcoinj.wallet.bip47.BIP47Util;
import org.bitcoinj.wallet.bip47.Bip47Address;
import org.bitcoinj.wallet.bip47.Bip47Meta;
import org.bitcoinj.wallet.bip47.NotSecp256k1Exception;
import org.bitcoinj.wallet.bip47.PaymentCode;
import org.bitcoinj.wallet.bip47.SecretPoint;
import org.bitcoinj.wallet.bip47.listeners.BlockchainDownloadProgressTracker;
import org.bitcoinj.wallet.bip47.listeners.TransactionEventListener;
import org.bitcoinj.crypto.bip47.Account;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.bitcoinj.crypto.ChildNumber;
import org.bitcoinj.crypto.DeterministicKey;
import org.bitcoinj.crypto.HDKeyDerivation;
import org.bitcoinj.net.discovery.DnsDiscovery;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.store.BlockStore;
import org.bitcoinj.store.BlockStoreException;
import org.bitcoinj.store.SPVBlockStore;
import org.bitcoinj.wallet.bip47.models.StashDeterministicSeed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.Charset;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.spec.InvalidKeySpecException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

/**
 * Created by jimmy on 9/28/17.
 */

/**
 * <p>A {@link Wallet} that runs in SPV mode and supports BIP 47 payments for coins BTC, BCH, tBTC and tBCH. </p>
 *
 * <p>To use it, you will provide a designated directory where this class will store the coin wallets and the SPV chains.</p>
 *
 * <p>By calling {@link Wallet.start()}, this wallet will automatically import payment addresses when a Bip 47
 * notification transaction is received.</p>
 *
 */
public class Wallet extends org.bitcoinj.wallet.Wallet {
    private volatile BlockChain vChain;
    private volatile BlockStore vStore;
    private volatile PeerGroup vPeerGroup;

    private volatile File directory;
    private String coin;

    private StashDeterministicSeed restoreFromSeed;

    private List<Account> mAccounts = new ArrayList<>(1);

    private BlockchainDownloadProgressTracker mBlockchainDownloadProgressTracker;
    private TransactionEventListener mTransactionEventListener = null;

    private boolean mBlockchainDownloadStarted = false;
    private ConcurrentHashMap<String, Bip47Meta> bip47MetaData = new ConcurrentHashMap<>();
    private static final Logger log = LoggerFactory.getLogger(Wallet.class);

    public Wallet(NetworkParameters params, File directory, String coin, @Nullable StashDeterministicSeed deterministicSeed) throws Exception {
        super(params);
        Context.propagate(new Context(getNetworkParameters()));
        this.directory = new File(directory, coin);;

        if (!directory.exists()) {
            if (!directory.mkdirs()) {
                throw new IOException("Could not create directory " + directory.getAbsolutePath());
            }
        }

        this.coin = coin;
        this.restoreFromSeed = deterministicSeed;

        File walletFile = getWalletFile();
        boolean chainFileExists = getChainFile().exists();
        boolean shouldReplayWallet = (walletFile.exists() && !chainFileExists) || deterministicSeed != null;

        Context.propagate(new Context(getNetworkParameters()));

        org.bitcoinj.wallet.Wallet coreWallet;

        if (getWalletFile().exists()) {
            coreWallet = loadWallet(getNetworkParameters(), shouldReplayWallet, walletFile);
        } else {
            coreWallet = createWallet(getNetworkParameters());
            coreWallet.freshReceiveKey();
            coreWallet.saveToFile(walletFile);
            coreWallet = loadWallet(getNetworkParameters(), false, walletFile);
        }

        checkNotNull(coreWallet);
        autosaveToFile(walletFile, 5, TimeUnit.SECONDS, null);

        addWatchedScripts(coreWallet.getWatchedScripts());
        if (coreWallet.getDescription() != null) {
            setDescription(coreWallet.getDescription());
        }

        if (shouldReplayWallet) {
            // Should mirror Wallet.reset()
            setLastBlockSeenHash(null);
            setLastBlockSeenHeight(-1);
            setLastBlockSeenTimeSecs(0);
        } else {
            // Update the lastBlockSeenHash.
            setLastBlockSeenHash(coreWallet.getLastBlockSeenHash());
            setLastBlockSeenHeight(coreWallet.getLastBlockSeenHeight());
            setLastBlockSeenTimeSecs(coreWallet.getLastBlockSeenTimeSecs());

            // Update transaction outputs to point to inputs that spend them
            Iterator<WalletTransaction> iter = coreWallet.getWalletTransactions().iterator();
            while(iter.hasNext())
                addWalletTransaction(iter.next());

            if (coreWallet.getKeyRotationTime() != null)
                setKeyRotationTime(coreWallet.getKeyRotationTime());
        }

        //loadExtensions(wallet, extensions != null ? extensions : new WalletExtension[0], walletProto);

        this.tags = coreWallet.getTags();

        for (TransactionSigner signer : coreWallet.getTransactionSigners()) {
            addTransactionSigner(signer);
        }

        setVersion(coreWallet.getVersion());

        setAccount(params);

        Address notificationAddress = mAccounts.get(0).getNotificationAddress();
        log.debug("Wallet notification address: "+notificationAddress.toString());

        if (!coreWallet.isAddressWatched(notificationAddress)) {
            addWatchedAddress(notificationAddress);
        }

        allowSpendingUnconfirmedTransactions();


        // init
        File chainFile = getChainFile();

        // Initiate Bitcoin network objects (block store, blockchain and peer group)
        vStore = new SPVBlockStore(getNetworkParameters(), chainFile);
        if (restoreFromSeed != null && chainFileExists) {
            log.info( "Deleting the chain file in preparation from restore.");
            vStore.close();
            if (!chainFile.delete())
                log.warn("start: ", new IOException("Failed to delete chain file in preparation for restore."));
            vStore = new SPVBlockStore(getNetworkParameters(), chainFile);
        }
        vChain = new BlockChain(getNetworkParameters(), vStore);
        vPeerGroup = new PeerGroup(getNetworkParameters(), vChain);

        // Fixes a bug created by a race condition between a filteredBlock and a notification transaction
        // (transaction dependencies are asynchronous (See issue 1029))
        // By rolling the blockstore one block, we will be sure that the generated keys were imported to the wallet.
        vPeerGroup.addOnTransactionBroadcastListener(new OnTransactionBroadcastListener() {
            @Override
            public void onTransaction(Peer peer, Transaction t) {
                if (isNotificationTransaction(t)){
                    if (getTransaction(t.getHash())!=null)
                        return;
                    log.debug("Valid notification transaction found for the first time. Replaying a block back .. ");
                    try {
                        vChain.rollbackBlockStore(getLastBlockSeenHeight() - 1);
                    } catch(BlockStoreException e){
                        log.error("Could not rollback ... " );
                    }
                }
            }
        });

        if (getCoin().equals("BCH")) {
            vPeerGroup.addAddress(new PeerAddress(InetAddresses.forString("158.69.119.35"), 8333));
            vPeerGroup.addAddress(new PeerAddress(InetAddresses.forString("144.217.73.86"), 8333));
        } else if (getCoin().equals("tBCH")) {
            vPeerGroup.addAddress(new PeerAddress(InetAddresses.forString("158.69.119.35"), 8333));
            vPeerGroup.addAddress(new PeerAddress(InetAddresses.forString("144.217.73.86"), 18333));
        }

        vPeerGroup.setUseLocalhostPeerWhenPossible(true);
        vPeerGroup.addPeerDiscovery(new DnsDiscovery(getNetworkParameters()));

        vChain.addWallet(this);
        vPeerGroup.addWallet(this);

        log.debug(toString());
    }

    public Wallet(NetworkParameters params, KeyChainGroup kcg){
        super(params, kcg);
    }

    // Return the corresponding file for the wallet's coin
    protected File getWalletFile(){
        return new File(directory, getCoin() + ".wallet");
    }

    // Return the wallet's SPVChain
    protected File getChainFile(){
        return new File(directory, getCoin() + ".spvchain");
    }


    // When a payment channel is created with a BIP47 notification transaction, the first payment
    // can be in the same block or in the next block(s). In any case, the notification transaction will
    // contain a payment code from Bob. The payment code is used to import keys to this wallet.
    //
    // If a block arrives with a notification transaction, the block may not have waited enough for the keys
    // to be imported into this wallet and therefore missing the payment transaction in the block.
    //
    // We want to redownload a block if we have notification without a subsequent payment transaction
    private int blockNtxs = 0;
    @Override
    public boolean checkForFilterExhaustion(FilteredBlock block) {
        List<Transaction> txsInBlock = new ArrayList<>();

        for (Transaction tx : getTransactions()){
            if (tx.getConfidence().getAppearedAtChainHeight() - 1 == getLastBlockSeenHeight() ) {
                if (isNotificationTransaction(tx))
                    blockNtxs++;

                if (blockNtxs == 5)
                    return super.checkForFilterExhaustion(block);
            }
        }
        return super.checkForFilterExhaustion(block);
    }

    private static org.bitcoinj.wallet.Wallet loadWallet(NetworkParameters networkParameters, boolean shouldReplayWallet, File vWalletFile) throws Exception {
        try (FileInputStream walletStream = new FileInputStream(vWalletFile)) {
            Protos.Wallet proto = WalletProtobufSerializer.parseToProto(walletStream);
            final WalletProtobufSerializer serializer = new WalletProtobufSerializer();
            org.bitcoinj.wallet.Wallet wallet = serializer.readWallet(networkParameters, null, proto);
            if (shouldReplayWallet)
                wallet.reset();
            return wallet;
        }
    }

    private Wallet createWallet(NetworkParameters networkParameters) throws IOException {
        KeyChainGroup kcg;
        if (restoreFromSeed != null)
            kcg = new KeyChainGroup(networkParameters, restoreFromSeed);
        else
            kcg = new KeyChainGroup(networkParameters);
        return new Wallet(networkParameters, kcg);  // default
    }

    public String getCoin() {
        return this.coin;
    }

    public void setAccount(NetworkParameters networkParameters) {
        log.debug("Seed: "+this.getKeyChainSeed());

        byte[] hd_seed = this.getKeyChainSeed().getSeedBytes();

        DeterministicKey mKey = HDKeyDerivation.createMasterPrivateKey(hd_seed);
        DeterministicKey purposeKey = HDKeyDerivation.deriveChildKey(mKey, 47 | ChildNumber.HARDENED_BIT);
        DeterministicKey coinKey = HDKeyDerivation.deriveChildKey(purposeKey, ChildNumber.HARDENED_BIT);
        Account account = new Account(networkParameters, coinKey, 0);

        mAccounts.clear();
        mAccounts.add(account);
    }

    public void start(boolean startBlockchainDownload) {
        if (startBlockchainDownload) {
            startBlockchainDownload();
        }
    }

    private void startBlockchainDownload() {
        if (isStarted() && !mBlockchainDownloadStarted) {
            log.debug("Starting blockchain download.");
            vPeerGroup.start();
            vPeerGroup.startBlockChainDownload(mBlockchainDownloadProgressTracker);
            mBlockchainDownloadStarted = true;
        }
    }

    public List<Peer> getConnectedPeers() {
        return vPeerGroup.getConnectedPeers();
    }

    public void stop() {
        if (vPeerGroup == null || !isStarted()) {
            return;
        }

        log.debug("Stopping peergroup");
        if (vPeerGroup.isRunning()) vPeerGroup.stopAsync();
        try {
            log.debug("Saving wallet");
            saveToFile(getWalletFile());
        } catch (IOException e) {
            e.printStackTrace();
        }
        log.debug("stopWallet: closing store");
        try {
            vStore.close();
        } catch (BlockStoreException e) {
            e.printStackTrace();
        }

        vStore = null;

        mBlockchainDownloadStarted = false;

        log.debug("stopWallet: Done");
    }

    public boolean isStarted() {
        return vStore != null;
    }

    public void setBlockchainDownloadProgressTracker(BlockchainDownloadProgressTracker downloadProgressTracker) {
        mBlockchainDownloadProgressTracker = downloadProgressTracker;
    }

    /**
     *
     */
    public boolean loadBip47MetaData() {
        String jsonString = readBip47MetaDataFile();

        if (StringUtils.isEmpty(jsonString)) {
            return false;
        }

        log.debug("loadBip47MetaData: "+jsonString);

        return importBip47MetaData(jsonString);
    }

    public String readBip47MetaDataFile() {
        File file = new File(directory, getCoin().concat(".bip47"));
        String jsonString;
        try {
            jsonString = FileUtils.readFileToString(file, Charset.defaultCharset());
        } catch (IOException e){
            log.debug("Creating BIP47 wallet file at " + file.getAbsolutePath() + "  ...");
            saveBip47MetaData();
            loadBip47MetaData();
            return null;
        }

        return jsonString;
    }

    public boolean importBip47MetaData(String jsonString) {
        log.debug("loadBip47MetaData: "+jsonString);

        Gson gson = new Gson();
        Type collectionType = new TypeToken<Collection<Bip47Meta>>(){}.getType();
        try {
            List<Bip47Meta> bip47MetaList = gson.fromJson(jsonString, collectionType);
            if (bip47MetaList != null) {
                for (Bip47Meta bip47Meta: bip47MetaList) {
                    bip47MetaData.put(bip47Meta.getPaymentCode(), bip47Meta);
                }
            }
        } catch (JsonSyntaxException e) {
            return true;
        }
        return false;
    }

    public synchronized void saveBip47MetaData() {
        try {
            saveToFile(getWalletFile());
        } catch (IOException io){
            log.error("Failed to save wallet file",io);
        }

        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        String json = gson.toJson(bip47MetaData.values());

        log.debug("saveBip47MetaData: "+json);

        File file = new File(directory, getCoin().concat(".bip47"));

        try {
            FileUtils.writeStringToFile(file, json, Charset.defaultCharset(), false);
            log.debug("saveBip47MetaData: saved");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void addTransactionEventListener(TransactionEventListener coinsReceivedEventListener) {
        if (mTransactionEventListener != null) {
            removeCoinsReceivedEventListener(mTransactionEventListener);
            removeTransactionConfidenceEventListener(mTransactionEventListener);
        }

        removeCoinsReceivedEventListener(coinsReceivedEventListener);
        removeTransactionConfidenceEventListener(coinsReceivedEventListener);

        coinsReceivedEventListener.setWallet(this);

        addCoinsReceivedEventListener(coinsReceivedEventListener);
        addTransactionConfidenceEventListener(coinsReceivedEventListener);

        mTransactionEventListener = coinsReceivedEventListener;
    }

    public boolean isNotificationTransaction(Transaction tx) {
        Address address = getAddressOfReceived(tx);
        Address myNotificationAddress = mAccounts.get(0).getNotificationAddress();

        return address != null && address.toString().equals(myNotificationAddress.toString());
    }

    public boolean isToBIP47Address(Transaction transaction) {
        List<ECKey> keys = getImportedKeys();
        for (ECKey key : keys) {
            Address address = key.toAddress(getNetworkParameters());
            if (address == null) {
                continue;
            }
            Address addressOfReceived = getAddressOfReceived(transaction);
            if (addressOfReceived != null && address.toString().equals(addressOfReceived.toString())) {
                return true;
            }
        }
        return false;
    }

    public Address getAddressOfReceived(Transaction tx) {
        for (final TransactionOutput output : tx.getOutputs()) {
            try {
                if (output.isMineOrWatched(this)) {
                    final Script script = output.getScriptPubKey();
                    return script.getToAddress(getNetworkParameters(), true);
                }
            } catch (final ScriptException x) {
                // swallow
            }
        }

        return null;
    }

    public Address getAddressOfSent(Transaction tx) {
        for (final TransactionOutput output : tx.getOutputs()) {
            try {
                if (!output.isMineOrWatched(this)) {
                    final Script script = output.getScriptPubKey();
                    return script.getToAddress(getNetworkParameters(), true);
                }
            } catch (final ScriptException x) {
                // swallow
            }
        }

        return null;
    }

    public PaymentCode getPaymentCodeInNotificationTransaction(Transaction tx) {
        byte[] privKeyBytes = mAccounts.get(0).getNotificationKey().getPrivKeyBytes();

        return BIP47Util.getPaymentCodeInNotificationTransaction(privKeyBytes, tx);
    }

    public boolean savePaymentCode(PaymentCode paymentCode) {
        if (bip47MetaData.containsKey(paymentCode.toString())) {
            Bip47Meta bip47Meta = bip47MetaData.get(paymentCode.toString());
            if (bip47Meta.getIncomingAddresses().size() != 0) {
                return false;
            } else {
                try {
                    bip47Meta.generateKeys(this);
                    return true;
                } catch (NotSecp256k1Exception | InvalidKeyException | InvalidKeySpecException | NoSuchAlgorithmException | NoSuchProviderException e) {
                    e.printStackTrace();
                    return false;
                }
            }
        }

        Bip47Meta bip47Meta = new Bip47Meta(paymentCode.toString());

        try {
            bip47Meta.generateKeys(this);
            bip47MetaData.put(paymentCode.toString(), bip47Meta);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }

        return true;
    }

    public Account getAccount(int i) {
        return mAccounts.get(i);
    }

    public Address getAddressOfKey(ECKey key) {
        return key.toAddress(getNetworkParameters());
    }

    public boolean generateNewBip47IncomingAddress(String address) {
        for (Bip47Meta bip47Meta : bip47MetaData.values()) {
            for (Bip47Address bip47Address : bip47Meta.getIncomingAddresses()) {
                if (!bip47Address.getAddress().equals(address)) {
                    continue;
                }
                if (bip47Address.isSeen()) {
                    return false;
                }

                int nextIndex = bip47Meta.getCurrentIncomingIndex() + 1;
                try {
                    ECKey key = BIP47Util.getReceiveAddress(this, bip47Meta.getPaymentCode(), nextIndex).getReceiveECKey();
                    importKey(key);
                    Address newAddress = getAddressOfKey(key);
                    bip47Meta.addNewIncomingAddress(newAddress.toString(), nextIndex);
                    bip47Address.setSeen(true);
                    return true;
                } catch (Exception e) {
                    e.printStackTrace();
                }
                return false;
            }
        }
        return false;
    }

    public Bip47Meta getBip47MetaForAddress(String address) {
        for (Bip47Meta bip47Meta : bip47MetaData.values()) {
            for (Bip47Address bip47Address : bip47Meta.getIncomingAddresses()) {
                if (bip47Address.getAddress().equals(address)) {
                    return bip47Meta;
                }
            }
        }
        return null;
    }

    public String getPaymentCodeForAddress(String address) {
        for (Bip47Meta bip47Meta : bip47MetaData.values()) {
            for (Bip47Address bip47Address : bip47Meta.getIncomingAddresses()) {
                if (bip47Address.getAddress().equals(address)) {
                    return bip47Meta.getPaymentCode();
                }
            }
        }
        return null;
    }

    public Bip47Meta getBip47MetaForOutgoingAddress(String address) {
        for (Bip47Meta bip47Meta : bip47MetaData.values()) {
            for (String outgoingAddress : bip47Meta.getOutgoingAddresses()) {
                if (outgoingAddress.equals(address)) {
                    return bip47Meta;
                }
            }
        }
        return null;
    }

    public Bip47Meta getBip47MetaForPaymentCode(String paymentCode) {
        for (Bip47Meta bip47Meta : bip47MetaData.values()) {
            if (bip47Meta.getPaymentCode().equals(paymentCode)) {
                return bip47Meta;
            }
        }
        return null;
    }

    public Coin getValueOfTransaction(Transaction transaction) {
        return transaction.getValue(this);
    }

    public Coin getValueSentToMe(Transaction transaction) {
        return transaction.getValueSentToMe(this);
    }

    public Coin getValueSentFromMe(Transaction transaction) {
        return transaction.getValueSentFromMe(this);
    }

    public List<Transaction> getTransactions() {
        return getTransactionsByTime();
    }

    public long getBalanceValue() {
        return getBalance(org.bitcoinj.wallet.Wallet.BalanceType.ESTIMATED_SPENDABLE).getValue();
    }

    public Coin getBalance() {
        return getBalance(org.bitcoinj.wallet.Wallet.BalanceType.ESTIMATED_SPENDABLE);
    }

    public boolean isDownloading() {
        return mBlockchainDownloadProgressTracker != null && mBlockchainDownloadProgressTracker.isDownloading();
    }

    public double getBlockchainProgress() {
        return mBlockchainDownloadProgressTracker != null ? mBlockchainDownloadProgressTracker.getProgress() : -1d;
    }

    public boolean isTransactionEntirelySelf(Transaction tx) {
        for (final TransactionInput input : tx.getInputs()) {
            final TransactionOutput connectedOutput = input.getConnectedOutput();
            if (connectedOutput == null || !connectedOutput.isMine(this))
                return false;
        }

        for (final TransactionOutput output : tx.getOutputs()) {
            if (!output.isMine(this))
                return false;
        }

        return true;
    }

    public String getPaymentCode() {
        return getAccount(0).getStringPaymentCode();
    }

    public void resetBlockchainSync() {
        File chainFile = new File(directory, getCoin()+".spvchain");
        if (chainFile.exists()) {
            log.debug("deleteSpvFile: exits");
            chainFile.delete();
        }
    }

    public String getMnemonicCode() {
        return Joiner.on(" ").join(getKeyChainSeed().getMnemonicCode());
    }

    public Address getCurrentAddress() {
        return currentReceiveAddress();
    }

    public Address getAddressFromBase58(String addr) {
        return Address.fromBase58(getNetworkParameters(), addr);
    }

    public boolean isValidAddress(String address) {
        try {
            Address.fromBase58(getNetworkParameters(), address);
            return true;
        } catch (AddressFormatException e) {
            try {
                CashAddress.decode(address);
                return true;
            } catch (AddressFormatException e2) {
                e2.printStackTrace();
                return false;
            }
        }
    }

    public Transaction createSend(String strAddr, long amount) throws InsufficientMoneyException {
        Address address;
        try {
            address = Address.fromBase58(getNetworkParameters(), strAddr);
        } catch (AddressFormatException e1) {
            try {
                address = CashAddress.decode(strAddr);
            } catch (AddressFormatException e2) {
                return null;
            }
        }
        return createSend(address, amount);
    }

    public Transaction createSend(Address address, long amount) throws InsufficientMoneyException {
        SendRequest sendRequest = SendRequest.to(address, Coin.valueOf(amount));
        if (!getNetworkParameters().getUseForkId()) {
            sendRequest.feePerKb = Coin.valueOf(141000);
        }
        completeTx(sendRequest);
        return sendRequest.tx;
    }

    public SendRequest makeNotificationTransaction(String paymentCode, boolean complete) throws InsufficientMoneyException {
        Account toAccount = new Account(getNetworkParameters(), paymentCode);
        Coin ntValue =  getNetworkParameters().getMinNonDustOutput();
        Address ntAddress = toAccount.getNotificationAddress();


        log.debug("Balance: " + getBalance());
        log.debug("To notification address: "+ntAddress.toString());
        log.debug("Value: "+ntValue.toFriendlyString());

        SendRequest sendRequest = SendRequest.to(ntAddress, ntValue);

        if (!getNetworkParameters().getUseForkId()) {
            sendRequest.feePerKb = Coin.valueOf(141000);
        }

        sendRequest.memo = "notification_transaction";

        FeeCalculation feeCalculation = WalletUtil.calculateFee(this, sendRequest, ntValue, calculateAllSpendCandidates());

        for (TransactionOutput output :feeCalculation.bestCoinSelection.gathered) {
            sendRequest.tx.addInput(output);
        }

        if (sendRequest.tx.getInputs().size() > 0) {
            TransactionInput txIn = sendRequest.tx.getInput(0);
            RedeemData redeemData = txIn.getConnectedRedeemData(this);
            checkNotNull(redeemData, "StashTransaction exists in wallet that we cannot redeem: %s", txIn.getOutpoint().getHash());
            log.debug("Keys: "+redeemData.keys.size());
            log.debug("Private key 0?: "+redeemData.keys.get(0).hasPrivKey());
            byte[] privKey = redeemData.getFullKey().getPrivKeyBytes();
            log.debug("Private key: "+ Utils.HEX.encode(privKey));
            byte[] pubKey = toAccount.getNotificationKey().getPubKey();
            log.debug("Public Key: "+Utils.HEX.encode(pubKey));
            byte[] outpoint = txIn.getOutpoint().bitcoinSerialize();

            byte[] mask = null;
            try {
                SecretPoint secretPoint = new SecretPoint(privKey, pubKey);
                log.debug("Secret Point: "+Utils.HEX.encode(secretPoint.ECDHSecretAsBytes()));
                log.debug("Outpoint: "+Utils.HEX.encode(outpoint));
                mask = PaymentCode.getMask(secretPoint.ECDHSecretAsBytes(), outpoint);
            } catch (InvalidKeyException | NoSuchAlgorithmException | InvalidKeySpecException | NoSuchProviderException e) {
                e.printStackTrace();
            }
            log.debug("My payment code: "+mAccounts.get(0).getPaymentCode().toString());
            log.debug("Mask: "+Utils.HEX.encode(mask));
            byte[] op_return = PaymentCode.blind(mAccounts.get(0).getPaymentCode().getPayload(), mask);

            sendRequest.tx.addOutput(Coin.ZERO, ScriptBuilder.createOpReturnScript(op_return));
        }

        if (complete)
            completeTx(sendRequest);

        log.debug("Completed SendRequest");
        log.debug(sendRequest.toString());
        log.debug(sendRequest.tx.toString());

        sendRequest.tx.verify();

        return sendRequest;
    }

    public Transaction getSignedNotificationTransaction(SendRequest sendRequest, String paymentCode) {
        commitTx(sendRequest.tx);
        return sendRequest.tx;
    }

    public ListenableFuture<Transaction> broadcastTransaction(Transaction transactionToSend) {
        commitTx(transactionToSend);
        return vPeerGroup.broadcastTransaction(transactionToSend).future();
    }

    public boolean putBip47Meta(String profileId, String name) {
        if (bip47MetaData.containsKey(profileId)) {
            Bip47Meta bip47Meta = bip47MetaData.get(profileId);
            if (!name.equals(bip47Meta.getLabel())) {
                bip47Meta.setLabel(name);
                return true;
            }
        } else {
            bip47MetaData.put(profileId, new Bip47Meta(profileId, name));
            return true;
        }
        return false;
    }

    public void putPaymenCodeStatusSent(String paymentCode) {
        if (bip47MetaData.containsKey(paymentCode)) {
            Bip47Meta bip47Meta = bip47MetaData.get(paymentCode);
            bip47Meta.setStatusSent();
        } else {
            putBip47Meta(paymentCode, paymentCode);
            putPaymenCodeStatusSent(paymentCode);
        }
    }

    public String getCurrentOutgoingAddress(Bip47Meta bip47Meta) {
        try {
            ECKey key = BIP47Util.getSendAddress(this, new PaymentCode(bip47Meta.getPaymentCode()), bip47Meta.getCurrentOutgoingIndex()).getSendECKey();
            return key.toAddress(getNetworkParameters()).toString();
        } catch (InvalidKeyException | InvalidKeySpecException | NotSecp256k1Exception | NoSuchProviderException | NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return null;
    }

    static class FeeCalculation {
        CoinSelection bestCoinSelection;
        TransactionOutput bestChangeOutput;
    }
}
