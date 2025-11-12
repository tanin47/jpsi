package tanin.ejwf;

import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.x509.*;
import org.bouncycastle.cert.CertIOException;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import javax.security.auth.x500.X500Principal;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.*;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.Date;

// Generating a self-signed certificate is critical to preventing a MITM attack.
public record SelfSignedCertificate(KeyPair keyPair, X509Certificate cert) {

  public static File generateKeyStoreFile(SelfSignedCertificate cert, String keyStorePassword) throws KeyStoreException, CertificateException, IOException, NoSuchAlgorithmException, OperatorCreationException {
    var pass = keyStorePassword.toCharArray();
    var params = new KeyStore.PasswordProtection(pass);
    var keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
    keyStore.load(null, pass);
    keyStore.setEntry(
      "localhost",
      new KeyStore.PrivateKeyEntry(cert.keyPair.getPrivate(), new X509Certificate[] { cert.cert }),
      params
    );
    var file = File.createTempFile("keystore", ".jks");
    file.deleteOnExit();
    try (var fos = new FileOutputStream(file)) {
      keyStore.store(fos, pass);
    }
    return file;
  }

  public static SelfSignedCertificate generate(String host) throws CertIOException, OperatorCreationException, CertificateException, NoSuchAlgorithmException {
    Security.addProvider(new BouncyCastleProvider());

    X500Principal signedByPrincipal = new X500Principal("CN=" + host);
    KeyPair signedByKeyPair = generateKeyPair();

    long notBefore = System.currentTimeMillis();
    long notAfter = notBefore + (1000L * 3600L * 24 * 365);

    ASN1Encodable[] encodableAltNames = new ASN1Encodable[]{new GeneralName(GeneralName.dNSName, host)};
    KeyPurposeId[] purposes = new KeyPurposeId[]{KeyPurposeId.id_kp_serverAuth, KeyPurposeId.id_kp_clientAuth};

    X509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(signedByPrincipal,
    BigInteger.ONE, new Date(notBefore), new Date(notAfter), signedByPrincipal, signedByKeyPair.getPublic());

    certBuilder.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));
    certBuilder.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.digitalSignature + KeyUsage.keyEncipherment));
    certBuilder.addExtension(Extension.extendedKeyUsage, false, new ExtendedKeyUsage(purposes));
    certBuilder.addExtension(Extension.subjectAlternativeName, false, new DERSequence(encodableAltNames));

    final ContentSigner signer = new JcaContentSignerBuilder(("SHA256withRSA")).build(signedByKeyPair.getPrivate());
    X509CertificateHolder certHolder = certBuilder.build(signer);

    return new SelfSignedCertificate(signedByKeyPair, new JcaX509CertificateConverter().getCertificate(certHolder));
  }

  private static KeyPair generateKeyPair() throws NoSuchAlgorithmException {
    KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
    keyPairGenerator.initialize(2048, new SecureRandom());
    return keyPairGenerator.generateKeyPair();
  }

  public static String getSHA256Fingerprint(byte[] bs) throws NoSuchAlgorithmException, CertificateEncodingException {
    // Get an instance of the SHA-256 MessageDigest
    MessageDigest md = MessageDigest.getInstance("SHA-256");

    // Calculate the hash of the certificate's encoded form
    byte[] fingerprintBytes = md.digest(bs);

    // Convert the byte array to a hexadecimal string
    StringBuilder sb = new StringBuilder();
    for (byte b : fingerprintBytes) {
      sb.append(String.format("%02x", b));
    }
    return sb.toString();
  }

  public static String generateRandomString() {
    byte[] randomBytes = new byte[32];
    new SecureRandom().nextBytes(randomBytes);
    return Base64.getEncoder().encodeToString(randomBytes);
  }
}
