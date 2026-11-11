package com.company.product;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;

final class AwsMqttCredentials {
    private static final String PREFS = "gasguard_aws_credentials";
    private static final String PREF_ENCRYPTED_MATERIAL = "encrypted_material";
    private static final String KEY_ALIAS = "gasguard_aws_material_key";
    private static final byte[] AES_MAGIC = "GG01".getBytes(StandardCharsets.US_ASCII);

    private AwsMqttCredentials() {}

    static boolean isConfigured(Context context) {
        return preferences(context).contains(PREF_ENCRYPTED_MATERIAL);
    }

    static void save(Context context, byte[] certificatePem, byte[] privateKeyPem) throws Exception {
        ByteBuffer material = ByteBuffer.allocate(4 + certificatePem.length + privateKeyPem.length);
        material.putInt(certificatePem.length);
        material.put(certificatePem);
        material.put(privateKeyPem);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateEncryptionKey());
        byte[] encrypted = cipher.doFinal(material.array());
        byte[] iv = cipher.getIV();
        ByteBuffer stored = ByteBuffer.allocate(AES_MAGIC.length + 1 + iv.length + encrypted.length);
        stored.put(AES_MAGIC);
        stored.put((byte) iv.length);
        stored.put(iv);
        stored.put(encrypted);

        boolean saved = preferences(context).edit()
                .putString(PREF_ENCRYPTED_MATERIAL, Base64.encodeToString(stored.array(), Base64.NO_WRAP))
                .commit();
        Arrays.fill(material.array(), (byte) 0);
        Arrays.fill(privateKeyPem, (byte) 0);
        if (!saved) throw new IllegalStateException("No se pudo guardar el certificado en el dispositivo.");
    }

    static SSLSocketFactory createSocketFactory(Context context) throws Exception {
        byte[] saved = Base64.decode(preferences(context).getString(PREF_ENCRYPTED_MATERIAL, ""), Base64.NO_WRAP);
        ByteBuffer stored = ByteBuffer.wrap(saved);
        byte[] magic = new byte[AES_MAGIC.length];
        stored.get(magic);
        if (!Arrays.equals(AES_MAGIC, magic)) throw new IllegalStateException("El certificado guardado no es válido.");
        int ivLength = stored.get() & 0xff;
        byte[] iv = new byte[ivLength];
        stored.get(iv);
        byte[] encrypted = new byte[stored.remaining()];
        stored.get(encrypted);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateEncryptionKey(), new GCMParameterSpec(128, iv));
        byte[] plaintext = cipher.doFinal(encrypted);
        ByteBuffer material = ByteBuffer.wrap(plaintext);
        int certificateLength = material.getInt();
        if (certificateLength < 1 || certificateLength > material.remaining()) {
            Arrays.fill(plaintext, (byte) 0);
            throw new IllegalStateException("El certificado guardado está incompleto.");
        }
        byte[] certificatePem = new byte[certificateLength];
        material.get(certificatePem);
        byte[] privateKeyPem = new byte[material.remaining()];
        material.get(privateKeyPem);

        Certificate certificate = CertificateFactory.getInstance("X.509")
                .generateCertificate(new ByteArrayInputStream(certificatePem));
        PrivateKey privateKey = parsePrivateKey(privateKeyPem);

        char[] keyPassword = "GasGuardKey".toCharArray();
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(null, null);
        keyStore.setKeyEntry("gasguard-device", privateKey, keyPassword, new Certificate[]{certificate});

        KeyManagerFactory keyManagers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagers.init(keyStore, keyPassword);
        TrustManagerFactory trustManagers = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagers.init((KeyStore) null);

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(keyManagers.getKeyManagers(), trustManagers.getTrustManagers(), null);
        Arrays.fill(plaintext, (byte) 0);
        Arrays.fill(privateKeyPem, (byte) 0);
        Arrays.fill(certificatePem, (byte) 0);
        Arrays.fill(keyPassword, '\0');
        return sslContext.getSocketFactory();
    }

    private static SecretKey getOrCreateEncryptionKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        if (keyStore.containsAlias(KEY_ALIAS)) return (SecretKey) keyStore.getKey(KEY_ALIAS, null);

        KeyGenerator keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        keyGenerator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build());
        return keyGenerator.generateKey();
    }

    private static PrivateKey parsePrivateKey(byte[] pemBytes) throws Exception {
        String pem = new String(pemBytes, StandardCharsets.US_ASCII);
        String begin;
        String end;
        if (pem.contains("-----BEGIN PRIVATE KEY-----")) {
            begin = "-----BEGIN PRIVATE KEY-----";
            end = "-----END PRIVATE KEY-----";
        } else if (pem.contains("-----BEGIN RSA PRIVATE KEY-----")) {
            begin = "-----BEGIN RSA PRIVATE KEY-----";
            end = "-----END RSA PRIVATE KEY-----";
        } else {
            throw new IllegalArgumentException("La clave debe ser una clave privada RSA PEM sin contraseña.");
        }

        int beginIndex = pem.indexOf(begin) + begin.length();
        int endIndex = pem.indexOf(end, beginIndex);
        if (endIndex < 0) throw new IllegalArgumentException("El archivo de clave privada está incompleto.");
        byte[] keyDer = Base64.decode(
                pem.substring(beginIndex, endIndex).replaceAll("\\s", ""), Base64.DEFAULT);
        if ("-----BEGIN RSA PRIVATE KEY-----".equals(begin)) keyDer = wrapPkcs1AsPkcs8(keyDer);
        PrivateKey key = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(keyDer));
        Arrays.fill(keyDer, (byte) 0);
        Arrays.fill(pemBytes, (byte) 0);
        return key;
    }

    private static byte[] wrapPkcs1AsPkcs8(byte[] pkcs1) {
        byte[] algorithm = new byte[]{0x30, 0x0d, 0x06, 0x09, 0x2a, (byte) 0x86, 0x48, (byte) 0x86,
                (byte) 0xf7, 0x0d, 0x01, 0x01, 0x01, 0x05, 0x00};
        byte[] octetString = derValue(0x04, pkcs1);
        ByteBuffer content = ByteBuffer.allocate(3 + algorithm.length + octetString.length);
        content.put(new byte[]{0x02, 0x01, 0x00});
        content.put(algorithm);
        content.put(octetString);
        return derValue(0x30, content.array());
    }

    private static byte[] derValue(int tag, byte[] content) {
        int lengthBytes = content.length < 128 ? 1 : content.length <= 0xff ? 2 : content.length <= 0xffff ? 3 : 4;
        ByteBuffer result = ByteBuffer.allocate(1 + lengthBytes + content.length);
        result.put((byte) tag);
        if (content.length < 128) {
            result.put((byte) content.length);
        } else if (content.length <= 0xff) {
            result.put((byte) 0x81).put((byte) content.length);
        } else if (content.length <= 0xffff) {
            result.put((byte) 0x82).put((byte) (content.length >> 8)).put((byte) content.length);
        } else {
            result.put((byte) 0x83).put((byte) (content.length >> 16)).put((byte) (content.length >> 8)).put((byte) content.length);
        }
        result.put(content);
        return result.array();
    }

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
