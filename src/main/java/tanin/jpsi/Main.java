package tanin.javaelectron;

import com.eclipsesource.json.Json;
import com.renomad.minum.web.FullSystem;
import com.renomad.minum.web.Response;
import com.renomad.minum.web.StatusLine;
import org.bouncycastle.operator.OperatorCreationException;
import tanin.ejwf.MinumBuilder;
import tanin.ejwf.SelfSignedCertificate;
import tanin.jpsi.Browser;

import java.io.IOException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import static com.renomad.minum.web.RequestLine.Method.GET;
import static com.renomad.minum.web.RequestLine.Method.POST;

public class Main {
  private static final Logger logger = Logger.getLogger(Main.class.getName());

  static {
    try (var configFile = Main.class.getResourceAsStream("/ejwf_default_logging.properties")) {
      LogManager.getLogManager().readConfiguration(configFile);
      logger.info("The log config (default_logging.properties) has been loaded.");
    } catch (IOException e) {
      logger.warning("Could not load the log config file (default_logging.properties): " + e.getMessage());
    }
  }

  public static void main(String[] args) throws Exception {
    var cert = SelfSignedCertificate.generate("localhost");
    logger.info("The below can be verified by opening a browser to the https endpoint:");
    logger.info("  Certificate SHA-256 Fingerprint: " + SelfSignedCertificate.getSHA256Fingerprint(cert.cert().getEncoded()));
    logger.info("  Public Key SHA-256 Fingerprint: " + SelfSignedCertificate.getSHA256Fingerprint(cert.keyPair().getPublic().getEncoded()));

    var csrfToken = SelfSignedCertificate.generateRandomString();
    logger.info("The csrf token: " + csrfToken);
    var main = new Main(19999, cert, csrfToken);
    logger.info("Starting...");
    main.start();

    var browser = new Browser("http://localhost:19999", cert, csrfToken);
    browser.run();

    logger.info("Blocking...");
    main.minum.block();
    logger.info("Exiting");
  }

  int port;
  public FullSystem minum;
  SelfSignedCertificate cert;
  String csrfToken;

  public Main(int port, SelfSignedCertificate cert, String csrfToken) {
    this.port = port;
    this.cert = cert;
    this.csrfToken = csrfToken;
  }

  public void start() throws CertificateException, KeyStoreException, IOException, NoSuchAlgorithmException, OperatorCreationException {
    var keyStorePassword = SelfSignedCertificate.generateRandomString();
    var keyStoreFile = SelfSignedCertificate.generateKeyStoreFile(cert, keyStorePassword);
    logger.info("Generated keystore file: " + keyStoreFile);
    minum = MinumBuilder.build(port, keyStoreFile.getAbsolutePath(), keyStorePassword, csrfToken);
    var wf = minum.getWebFramework();

    logger.info("Registering Minum...");

    wf.registerPath(
      GET,
      "",
      r -> {
        logger.info("Serve /");
        String content = new String(Main.class.getResourceAsStream("/html/index.html").readAllBytes());
        return Response.htmlOk(content);
      }
    );

    wf.registerPath(
      GET,
      "healthcheck",
      req -> {
        return Response.buildResponse(StatusLine.StatusCode.CODE_200_OK, Map.of("Content-Type", "text/plain"), "OK EWJF");
      }
    );

    AtomicInteger counter = new AtomicInteger();

    wf.registerPath(
      POST,
      "ask-java",
      req -> {
        System.out.println(req.getHeaders().valueByKey("Csrf-Token"));
        var json = Json.parse(req.getBody().asString());
        var msg = json.asObject().get("msg").asString();
        System.out.println("Javascripts said: " + msg);
        Thread.sleep(5000);
        return Response.buildResponse(
          StatusLine.StatusCode.CODE_200_OK,
          Map.of("Content-Type", "application/json"),
          Json.object()
            .add("response", "Hello from Java (" + counter.getAndIncrement() + ")")
            .toString()
        );
      }
    );
    logger.info("Finishing registering...");
  }

  public void stop() {
    if (minum != null) {
      minum.shutdown();
    }
  }
}
