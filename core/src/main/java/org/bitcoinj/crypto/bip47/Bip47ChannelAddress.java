//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package org.bitcoinj.crypto.bip47;

import java.math.BigInteger;
import org.apache.commons.lang3.ArrayUtils;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Utils;
import org.bitcoinj.crypto.ChildNumber;
import org.bitcoinj.crypto.DeterministicKey;
import org.bitcoinj.crypto.HDKeyDerivation;

public class Bip47ChannelAddress {
    private ECKey ecKey;
    private byte[] pubKey;
    private NetworkParameters params;

    public Bip47ChannelAddress(NetworkParameters params, DeterministicKey cKey, int childNum) {
        this.params = params;
        DeterministicKey dk = HDKeyDerivation.deriveChildKey(cKey, new ChildNumber(childNum, false));
        if(dk.hasPrivKey()) {
            byte[] now = ArrayUtils.addAll(new byte[1], dk.getPrivKeyBytes());
            this.ecKey = ECKey.fromPrivate(new BigInteger(now), true);
        } else {
            this.ecKey = ECKey.fromPublicOnly(dk.getPubKey());
        }

        long now1 = Utils.now().getTime() / 1000L;
        this.ecKey.setCreationTimeSeconds(now1);
        this.pubKey = this.ecKey.getPubKey();
    }

    public byte[] getPubKey() {
        return this.pubKey;
    }

    public org.bitcoinj.core.Address getAddress() {
        return this.ecKey.toAddress(this.params);
    }

}
