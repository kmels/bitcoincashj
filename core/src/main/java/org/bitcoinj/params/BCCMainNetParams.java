/*
 * Copyright 2013 Google Inc.
 * Copyright 2015 Andreas Schildbach
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

import com.google.common.base.Preconditions;
import com.google.common.base.Stopwatch;
import org.bitcoinj.core.*;
import org.bitcoinj.store.BlockStore;
import org.bitcoinj.store.BlockStoreException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.*;

/**
 * Parameters for the main production network on which people trade goods and services.
 */
public class BCCMainNetParams extends AbstractBitcoinCashParams {
    public static final int MAINNET_MAJORITY_WINDOW = 1000;
    public static final int MAINNET_MAJORITY_REJECT_BLOCK_OUTDATED = 950;
    public static final int MAINNET_MAJORITY_ENFORCE_BLOCK_UPGRADE = 750;

    public static final int COPAY_ADDRESS_HEADER = 28;
    public static final int COPAY_P2SH_HEADER = 40;

    /**
     * Scheme part for Bitcoin Cash URIs.
     */
    public static final String BITCOIN_CASH_SCHEME = "bitcoincash";

    private static final Logger log = LoggerFactory.getLogger(BCCMainNetParams.class);

    // Aug, 1 hard fork
    int uahfHeight = 478559;
    int daaHeight;

    public BCCMainNetParams() {
        super();
        interval = INTERVAL;
        targetTimespan = TARGET_TIMESPAN;
        maxTarget = Utils.decodeCompactBits(0x1d00ffffL);
        dumpedPrivateKeyHeader = 128;
        addressHeader = 0;
        p2shHeader = 5;
        acceptableAddressCodes = new int[] { addressHeader, p2shHeader, COPAY_ADDRESS_HEADER, COPAY_P2SH_HEADER };
        port = 8333;
        packetMagic = 0xe3e1f3e8L;
        bip32HeaderPub = 0x0488B21E; //The 4 byte header that serializes in base58 to "xpub".
        bip32HeaderPriv = 0x0488ADE4; //The 4 byte header that serializes in base58 to "xprv"

        majorityEnforceBlockUpgrade = MAINNET_MAJORITY_ENFORCE_BLOCK_UPGRADE;
        majorityRejectBlockOutdated = MAINNET_MAJORITY_REJECT_BLOCK_OUTDATED;
        majorityWindow = MAINNET_MAJORITY_WINDOW;

        genesisBlock.setDifficultyTarget(0x1d00ffffL);
        genesisBlock.setTime(1231006505L);
        genesisBlock.setNonce(2083236893);
        id = ID_MAINNET;
        subsidyDecreaseBlockCount = 210000;
        spendableCoinbaseDepth = 100;
        String genesisHash = genesisBlock.getHashAsString();
        checkState(genesisHash.equals("000000000019d6689c085ae165831e934ff763ae46a2a6c172b3f1b60a8ce26f"),
                genesisHash);

        // This contains (at a minimum) the blocks which are not BIP30 compliant. BIP30 changed how duplicate
        // transactions are handled. Duplicated transactions could occur in the case where a coinbase had the same
        // extraNonce and the same outputs but appeared at different heights, and greatly complicated re-org handling.
        // Having these here simplifies block connection logic considerably.
        checkpoints.put(91722, Sha256Hash.wrap("00000000000271a2dc26e7667f8419f2e15416dc6955e5a6c6cdf3f2574dd08e"));
        checkpoints.put(91812, Sha256Hash.wrap("00000000000af0aed4792b1acee3d966af36cf5def14935db8de83d6f9306f2f"));
        checkpoints.put(91842, Sha256Hash.wrap("00000000000a4d0a398161ffc163c503763b1f4360639393e0e4c8e300e0caec"));
        checkpoints.put(91880, Sha256Hash.wrap("00000000000743f190a18c5577a3c2d2a1f610ae9601ac046a38084ccb7cd721"));
        checkpoints.put(200000, Sha256Hash.wrap("000000000000034a7dedef4a161fa058a2d67a173a90155f3a2fe6fc132e0ebf"));
        checkpoints.put(478559, Sha256Hash.wrap("000000000000000000651ef99cb9fcbe0dadde1d424bd9f15ff20136191a5eec"));
         // November 13th 2017 new DAA fork
        checkpoints.put(504031, Sha256Hash.wrap("0000000000000000011ebf65b60d0a3de80b8175be709d653b4c1a1beeb6ab9c"));
        // May 15th 2018 re-enable op_codes and 32 MB max block size
        checkpoints.put(530359, Sha256Hash.wrap("0000000000000000011ada8bd08f46074f44a8f155396f43e38acf9501c49103"));
        // Nov 15th 2018 activate LTOR, DSV op_code
        checkpoints.put(556767, Sha256Hash.wrap("0000000000000000004626ff6e3b936941d341c5932ece4357eeccac44e6d56c"));
        // May 15th 2019 activate Schnorr, segwit recovery
        checkpoints.put(582680, Sha256Hash.wrap("000000000000000001b4b8e36aec7d4f9671a47872cb9a74dc16ca398c7dcc18"));

        dnsSeeds = new String[] {
                "seed.bitcoinabc.org",
                "btccash-seeder.bitcoinunlimited.info",
                "seed.bitprim.org",
                "seed.deadalnix.me",
                "seeder.criptolayer.net"
        };
        httpSeeds = null;

        addrSeeds = null;

        /** Activation time at which the cash HF kicks in. */
        cashHardForkActivationTime = 1510600000l; // GMT: Monday, November 13, 2017 7:06:40 PM
        daaHeight = 504031+1;
    }

    private static BCCMainNetParams instance;
    public static synchronized BCCMainNetParams get() {
        if (instance == null) {
            instance = new BCCMainNetParams();
        }
        return instance;
    }

    @Override
    public String getPaymentProtocolId() {
        return PAYMENT_PROTOCOL_ID_MAINNET;
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
        return BITCOIN_CASH_SCHEME;
    }

    /**
     * The number that is one greater than the largest representable SHA-256
     * hash.
     */
    private static BigInteger LARGEST_HASH = BigInteger.ONE.shiftLeft(256);

    /**
     * To reduce the impact of timestamp manipulation, we select the block we are
     * basing our computation on via a median of 3.
     */
    StoredBlock GetSuitableBlock(StoredBlock pindex, BlockStore blockStore) throws BlockStoreException{
        //assert(pindex->nHeight >= 3);

        /**
         * In order to avoid a block is a very skewed timestamp to have too much
         * influence, we select the median of the 3 top most blocks as a starting
         * point.
         */
        StoredBlock blocks[] = new StoredBlock[3];
        blocks[2] = pindex;
        blocks[1] = pindex.getPrev(blockStore);
        blocks[0] = blocks[1].getPrev(blockStore);

        // Sorting network.
        if (blocks[0].getHeader().getTimeSeconds() > blocks[2].getHeader().getTimeSeconds()) {
            //std::swap(blocks[0], blocks[2]);
            StoredBlock temp = blocks[0];
            blocks[0] = blocks[2];
            blocks[2] = temp;
        }

        if (blocks[0].getHeader().getTimeSeconds() > blocks[1].getHeader().getTimeSeconds()) {
            //std::swap(blocks[0], blocks[1]);
            StoredBlock temp = blocks[0];
            blocks[0] = blocks[1];
            blocks[1] = temp;
        }

        if (blocks[1].getHeader().getTimeSeconds() > blocks[2].getHeader().getTimeSeconds()) {
            //std::swap(blocks[1], blocks[2]);
            StoredBlock temp = blocks[1];
            blocks[1] = blocks[2];
            blocks[2] = temp;
        }

        // We should have our candidate in the middle now.
        return blocks[1];
    }

    /**
     * Compute the next required proof of work using a weighted average of the
     * estimated hashrate per block.
     *
     * Using a weighted average ensure that the timestamp parameter cancels out in
     * most of the calculation - except for the timestamp of the first and last
     * block. Because timestamps are the least trustworthy information we have as
     * input, this ensures the algorithm is more resistant to malicious inputs.
     */
    protected void checkNextCashWorkRequired(StoredBlock pindexPrev,
                                   Block pblock, BlockStore blockStore) {
        // This cannot handle the genesis block and early blocks in general.
        //assert(pindexPrev);

        // Special difficulty rule for testnet:
        // If the new block's timestamp is more than 2* 10 minutes then allow
        // mining of a min-difficulty block.
        /*if (params.fPowAllowMinDifficultyBlocks &&
                (pblock->GetBlockTime() >
                        pindexPrev->GetBlockTime() + 2 * params.nPowTargetSpacing)) {
            return UintToArith256(params.powLimit).GetCompact();
        }*/

        // Compute the difficulty based on the full adjustement interval.
        int nHeight = pindexPrev.getHeight();
        Preconditions.checkState(nHeight >= this.interval);

        // Get the last suitable block of the difficulty interval.
        try {
            StoredBlock pindexLast = GetSuitableBlock(pindexPrev, blockStore);
            //assert (pindexLast);

            // Get the first suitable block of the difficulty interval.
            int nHeightFirst = nHeight - 144;

            StoredBlock pindexFirst = pindexPrev;

            for (int i = 144; i > 0; --i)
            {
                pindexFirst = pindexFirst.getPrev(blockStore);
                if(pindexFirst == null)
                    return;
            }

            pindexFirst = GetSuitableBlock(pindexFirst, blockStore);
            //assert (pindexFirst);

            // Compute the target based on time and work done during the interval.
            BigInteger nextTarget =
                    ComputeTarget(pindexFirst, pindexLast);

            verifyDifficulty(nextTarget, pblock);
        }
        catch (BlockStoreException x)
        {
            //this means we don't have enough blocks, yet.  let it go until we do.
            return;
        }
    }

}
