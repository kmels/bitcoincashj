/* Copyright (c) 2017 Stash
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.bitcoinj.wallet.bip47;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.ECKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.List;

/**
 * <p>A BIP47PaymentChannel enables a payer (Alice) to obtain different addresses for the recipient (Bob) such that
 * Bob's incoming addresses are created using a @{link Bip47Account} and are not easily linked to each other.</p>
 *
 * <p>When Alice sends a notification transaction to Bob, Bob can use this class to generate addresses to watch for Alice's
 * inputs.</p>
 */
public class Bip47PaymentChannel {
    private static final int STATUS_NOT_SENT = -1;
    private static final int STATUS_SENT_CFM = 1;
    // how many addresses to generate in the wallet
    private static final int LOOKAHEAD = 10;

    // The payment code of the sender of payments
    private String paymentCode;
    private String label = "";
    // The addresses that the sender will use to pay
    private List<Bip47Address> incomingAddresses = new ArrayList<>();
    // The addresses that the sender has used
    private List<String> outgoingAddresses = new ArrayList<>();
    private int status = STATUS_NOT_SENT;
    private int currentOutgoingIndex = 0;
    private int currentIncomingIndex = -1;

    private static final Logger log = LoggerFactory.getLogger(Bip47PaymentChannel.class);
    public Bip47PaymentChannel() {}

    public Bip47PaymentChannel(String paymentCode) {
        this.paymentCode = paymentCode;
    }

    public Bip47PaymentChannel(String paymentCode, String label) {
        this(paymentCode);
        this.label = label;
    }

    public String getPaymentCode() {
        return paymentCode;
    }

    public void setPaymentCode(String pc) {
        paymentCode = pc;
    }

    public List<Bip47Address> getIncomingAddresses() {
        return incomingAddresses;
    }

    public int getCurrentIncomingIndex() {
        return currentIncomingIndex;
    }

    /** Imports the 10 next payment addresses to bip47Wallet.
     * @param bip47Wallet
     */
    public void generateKeys(Bip47Wallet bip47Wallet) throws NotSecp256k1Exception, NoSuchProviderException, NoSuchAlgorithmException, InvalidKeySpecException, InvalidKeyException {
        for (int i = 0; i < LOOKAHEAD; i++) {
            ECKey key = BIP47Util.getReceiveAddress(bip47Wallet, paymentCode, i).getReceiveECKey();
            Address address = bip47Wallet.getAddressOfKey(key);

            log.debug("New address generated");
            log.debug(address.toString());
            bip47Wallet.importKey(key);
            incomingAddresses.add(i, new Bip47Address(address.toString(), i));
        }

        currentIncomingIndex = LOOKAHEAD - 1;
    }

    public void addNewIncomingAddress(String newAddress, int nextIndex) {
        incomingAddresses.add(nextIndex, new Bip47Address(newAddress, nextIndex));
        currentIncomingIndex = nextIndex;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String l) {
        label = l;
    }

    public List<String> getOutgoingAddresses() {
        return outgoingAddresses;
    }

    public boolean isNotificationTransactionSent() {
        return status == STATUS_SENT_CFM;
    }

    public void setStatusSent() {
        status = STATUS_SENT_CFM;
    }

    public int getCurrentOutgoingIndex() {
        return currentOutgoingIndex;
    }

    public void incrementOutgoingIndex() {
        currentOutgoingIndex++;
    }

    public void addAddressToOutgoingAddresses(String address) {
        outgoingAddresses.add(address);
    }

    /* Use this method when */
    public void setStatusNotSent() {
        status = STATUS_NOT_SENT;
    }
}
