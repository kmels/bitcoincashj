/*
 * Copyright 2013 Google Inc.
 * Copyright 2014 Andreas Schildbach
 * Copyright 2018 the bitcoinj-cash developers
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.bitcoinj.params;

import java.math.BigInteger;
import java.util.Date;

import com.google.common.base.Preconditions;
import org.bitcoinj.core.*;
import org.bitcoinj.store.BlockStore;
import org.bitcoinj.store.BlockStoreException;

import static com.google.common.base.Preconditions.checkState;

/**
 * Parameters for the testnet, a separate public instance of Bitcoin that has relaxed rules suitable for development
 * and testing of applications and new Bitcoin versions.
 */
public class BCCTestNet3Params extends AbstractBitcoinCashParams {
    public static final int TESTNET_MAJORITY_WINDOW = 100;
    public static final int TESTNET_MAJORITY_REJECT_BLOCK_OUTDATED = 75;
    public static final int TESTNET_MAJORITY_ENFORCE_BLOCK_UPGRADE = 51;

    /**
     * Scheme part for Bitcoin Cash TestNet URIs.
     */
    public static final String BITCOIN_CASH_TESTNET_SCHEME = "bchtest";

    public BCCTestNet3Params() {
        super();
        id = ID_TESTNET;
        // Genesis hash is 000000000933ea01ad0ee984209779baaec3ced90fa3f408719526f8d77f4943
        packetMagic = 0xf4e5f3f4L;
        interval = INTERVAL;
        targetTimespan = TARGET_TIMESPAN;
        maxTarget = Utils.decodeCompactBits(0x1d00ffffL);
        port = 18333;
        addressHeader = 111;
        p2shHeader = 196;
        acceptableAddressCodes = new int[] { addressHeader, p2shHeader };
        dumpedPrivateKeyHeader = 239;
        genesisBlock.setTime(1296688602L);
        genesisBlock.setDifficultyTarget(0x1d00ffffL);
        genesisBlock.setNonce(414098458);
        spendableCoinbaseDepth = 100;
        subsidyDecreaseBlockCount = 210000;
        String genesisHash = genesisBlock.getHashAsString();
        checkState(genesisHash.equals("000000000933ea01ad0ee984209779baaec3ced90fa3f408719526f8d77f4943"));
        alertSigningKey = Utils.HEX.decode("04302390343f91cc401d56d68b123028bf52e5fca1939df127f63c6467cdf9c8e2c14b61104cf817d0b780da337893ecc4aaff1309e536162dabbdb45200ca2b0a");

        checkpoints.put(546, Sha256Hash.wrap("000000002a936ca763904c3c35fce2f3556c559c0214345d31b1bcebf76acb70"));
        checkpoints.put(1155875, Sha256Hash.wrap("00000000f17c850672894b9a75b63a1e72830bbd5f4c8889b5c1a80e7faef138"));
        // Nov, 13th 2017. DAA activation block.
        checkpoints.put(1188697, Sha256Hash.wrap("0000000000170ed0918077bde7b4d36cc4c91be69fa09211f748240dabe047fb"));
        // May 15th 2018, re-enabling opcodes, max block size 32MB
        checkpoints.put(1233070, Sha256Hash.wrap("0000000000000253c6201a2076663cfe4722e4c75f537552cc4ce989d15f7cd5"));
        // Nov 15th 2018, CHECKDATASIG, ctor
        checkpoints.put(1267996, Sha256Hash.wrap("00000000000001fae0095cd4bea16f1ce8ab63f3f660a03c6d8171485f484b24"));
        // May 15th 2019, Schnorr + segwit recovery activation block
        checkpoints.put(1303885, Sha256Hash.wrap("00000000000000479138892ef0e4fa478ccc938fb94df862ef5bde7e8dee23d3"));

        dnsSeeds = new String[] {
                "testnet-seed.bitcoinabc.org",
                "testnet-seeder.criptolayer.net",
                "testnet-seed.deadalnix.me",
                "testnet-seed.bitprim.org"
        };
        addrSeeds = null;
        bip32HeaderPub = 0x043587CF;
        bip32HeaderPriv = 0x04358394;

        majorityEnforceBlockUpgrade = TESTNET_MAJORITY_ENFORCE_BLOCK_UPGRADE;
        majorityRejectBlockOutdated = TESTNET_MAJORITY_REJECT_BLOCK_OUTDATED;
        majorityWindow = TESTNET_MAJORITY_WINDOW;

        // Aug, 1 hard fork
        uahfHeight = 1155876;

	// Nov, 13 hard fork
        daaUpdateHeight = 1188697;
        cashAddrPrefix = "bchtest";
    }

    private static BCCTestNet3Params instance;
    public static synchronized BCCTestNet3Params get() {
        if (instance == null) {
            instance = new BCCTestNet3Params();
        }
        return instance;
    }

    @Override
    public String getPaymentProtocolId() {
        return PAYMENT_PROTOCOL_ID_TESTNET;
    }

    @Override
    public int getMaxBlockSize() {
        return Block.BCC_MAX_BLOCK_SIZE;
    }

    @Override
    public int getMaxBlockSigops() {
        return Block.BCC_MAX_BLOCK_SIGOPS;
    }

    @Override
    public Coin getReferenceDefaultMinTxFee() {
        return Transaction.BCC_REFERENCE_DEFAULT_MIN_TX_FEE;
    }

    @Override
    public Coin getDefaultTxFee() {
        return Transaction.BCC_DEFAULT_TX_FEE;
    }

    @Override
    public Coin getMinNonDustOutput() {
        return Transaction.BCC_MIN_NONDUST_OUTPUT;
    }

    @Override
    public int getProtocolVersionNum(final ProtocolVersion version) {
        return version == ProtocolVersion.CURRENT? ProtocolVersion.BCC_CURRENT.getBitcoinProtocolVersion() : version.getBitcoinProtocolVersion();
    }

    @Override
    public boolean getUseForkId() {
        return true;
    }

    @Override
    public String getUriScheme() {
        return BITCOIN_CASH_TESTNET_SCHEME;
    }

    // February 16th 2012
    private static final Date testnetDiffDate = new Date(1329264000000L);

    public static boolean isValidTestnetDateBlock(Block block){
        return block.getTime().after(testnetDiffDate);
    }
}
