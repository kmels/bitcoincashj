package org.bitcoinj.wallet.bip47;

import org.bitcoinj.crypto.bip47.Bip47Account;
import org.bitcoinj.params.MainNetParams;
import org.junit.Test;

import static org.bitcoinj.core.Utils.HEX;
import static org.junit.Assert.assertEquals;

public class Bip47PaymentCodeTest {
    private final String ALICE_PAYMENT_CODE_V1 = "PM8TJTLJbPRGxSbc8EJi42Wrr6QbNSaSSVJ5Y3E4pbCYiTHUskHg13935Ubb7q8tx9GVbh2UuRnBc3WSyJHhUrw8KhprKnn9eDznYGieTzFcwQRya4GA";
    private final String ALICE_NOTIFICATION_ADDRESS = "1JDdmqFLhpzcUwPeinhJbUPw4Co3aWLyzW";
    private final String ALICE_NOTIFICATION_TESTADDRESS = "mxjb4tLKWrRsG3sGSMfgRPcFvCPkVgM4td";

    @Test
    public void pubKeyDeriveTests(){

        PaymentCode alice = new PaymentCode(ALICE_PAYMENT_CODE_V1);
        Bip47Account acc = new Bip47Account(MainNetParams.get(), ALICE_PAYMENT_CODE_V1);

        byte[] alice0th = alice.derivePubKeyAt(MainNetParams.get(),0);
        byte[] acc0th = acc.getNotificationKey().getPubKey();

        byte[] alice1st = alice.derivePubKeyAt(MainNetParams.get(),1);
        byte[] acc1st = acc.keyAt(1).getPubKey();

        assertEquals(HEX.encode(alice0th), HEX.encode(acc0th));
        assertEquals(HEX.encode(alice1st), HEX.encode(acc1st));

    }
}
