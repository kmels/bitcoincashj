/* Copyright (c) 2017 Stash
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.bitcoinj.crypto;

import java.math.BigInteger;
import java.security.*;
import java.security.spec.*;
import javax.crypto.KeyAgreement;
import javax.crypto.SecretKey;

import org.spongycastle.asn1.x9.ECNamedCurveTable;
import org.spongycastle.asn1.x9.X9ECParameters;
import org.spongycastle.util.encoders.Hex;

public class BIP47SecretPoint {
    private static final X9ECParameters params = ECNamedCurveTable.getByName("secp256k1");

    static {
        Security.addProvider(new org.spongycastle.jce.provider.BouncyCastleProvider());
    }

    private PrivateKey privKey = null;
    private PublicKey pubKey = null;
    private KeyFactory kf = null;

    public BIP47SecretPoint() {
    }

    public BIP47SecretPoint(byte[] dataPrv, byte[] dataPub) throws InvalidKeySpecException, InvalidKeyException, IllegalStateException, NoSuchAlgorithmException, NoSuchProviderException, InvalidParameterSpecException {
        this.kf = KeyFactory.getInstance("ECDH");
        this.privKey = this.loadPrivateKey(dataPrv);
        this.pubKey = this.loadPublicKey(dataPub);
    }

    public PrivateKey getPrivKey() {
        return this.privKey;
    }

    public void setPrivKey(PrivateKey privKey) {
        this.privKey = privKey;
    }

    public PublicKey getPubKey() {
        return this.pubKey;
    }

    public void setPubKey(PublicKey pubKey) {
        this.pubKey = pubKey;
    }

    public byte[] ECDHSecretAsBytes() throws InvalidKeyException, IllegalStateException, NoSuchAlgorithmException, NoSuchProviderException {
        return this.ECDHSecret().getEncoded();
    }

    public boolean isShared(BIP47SecretPoint secret) throws InvalidKeyException, NoSuchAlgorithmException, NoSuchProviderException {
        return this.equals(secret);
    }

    private SecretKey ECDHSecret() throws InvalidKeyException, IllegalStateException, NoSuchAlgorithmException, NoSuchProviderException {
        KeyAgreement ka = KeyAgreement.getInstance("ECDH");
        ka.init(this.privKey);
        ka.doPhase(this.pubKey, true);
        SecretKey secret = ka.generateSecret("AES");
        return secret;
    }

    private boolean equals(BIP47SecretPoint secret) throws InvalidKeyException, IllegalStateException, NoSuchAlgorithmException, NoSuchProviderException {
        return Hex.toHexString(this.ECDHSecretAsBytes()).equals(Hex.toHexString(secret.ECDHSecretAsBytes()));
    }

    private PublicKey loadPublicKey(byte[] data) throws InvalidKeySpecException, InvalidParameterSpecException, NoSuchProviderException, NoSuchAlgorithmException {
        org.spongycastle.math.ec.ECPoint P = params.getCurve().decodePoint(data);
        ECPoint pubPoint = new ECPoint(P.getXCoord().toBigInteger(), P.getYCoord().toBigInteger());
        AlgorithmParameters parameters = AlgorithmParameters.getInstance("ECDH", "BC");
        parameters.init(new ECGenParameterSpec("secp256k1"));
        ECParameterSpec ecParameters = parameters.getParameterSpec(ECParameterSpec.class);
        ECPublicKeySpec pubSpec = new ECPublicKeySpec(pubPoint, ecParameters);
        return this.kf.generatePublic(pubSpec);
    }

    private PrivateKey loadPrivateKey(byte[] data) throws InvalidKeySpecException, NoSuchProviderException, NoSuchAlgorithmException, InvalidParameterSpecException {
        AlgorithmParameters parameters = AlgorithmParameters.getInstance("ECDH");
        parameters.init(new ECGenParameterSpec("secp256k1"));
        ECParameterSpec ecParameters = parameters.getParameterSpec(ECParameterSpec.class);
        ECPrivateKeySpec privSpec = new ECPrivateKeySpec(new BigInteger(1, data), ecParameters);
        return this.kf.generatePrivate(privSpec);
    }
}
