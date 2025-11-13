package tanin.ejwf;

import com.renomad.minum.logging.Logger;
import com.renomad.minum.state.Constants;
import com.renomad.minum.state.Context;
import com.renomad.minum.web.*;
import org.bouncycastle.operator.OperatorCreationException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

import static com.renomad.minum.web.RequestLine.Method.GET;
import static com.renomad.minum.web.StatusLine.StatusCode.CODE_403_FORBIDDEN;

public class MinumBuilder {
  private static final java.util.logging.Logger logger = java.util.logging.Logger.getLogger(MinumBuilder.class.getName());
  private static String inferContentType(String assetPath) {
    var extension = assetPath.substring(assetPath.lastIndexOf(".") + 1).toLowerCase();
    return switch (extension) {
      case "js" -> "application/javascript";
      case "css" -> "text/css";
      case "png" -> "image/png";
      case "jpg", "jpeg" -> "image/jpeg";
      case "gif" -> "image/gif";
      case "svg" -> "image/svg+xml";
      case "ico" -> "image/x-icon";
      case "woff" -> "font/woff";
      case "woff2" -> "font/woff2";
      case "ttf" -> "font/ttf";
      case "eot" -> "application/vnd.ms-fontobject";
      default -> "application/octet-stream";
    };
  }


  public static final boolean IS_LOCAL_DEV = Files.exists(Path.of("local_dev_marker.ejwf"));

  public static FullSystem build(int port, String keyStorePath, String keyStorePassword, String csrfToken) throws CertificateException, KeyStoreException, IOException, NoSuchAlgorithmException, OperatorCreationException {
    if (IS_LOCAL_DEV) {
      logger.info("Running in the local development mode. Hot-Reload Module is enabled. `npm run hmr` must be running in a separate terminal");
    } else {
      logger.info("Running in the production mode.");
    }

    var props = new Properties();
    props.setProperty("SERVER_PORT", "" + port);
    props.setProperty("SSL_SERVER_PORT", "8444");
//    props.setProperty("LOG_LEVELS", "ASYNC_ERROR,AUDIT");
    props.setProperty("IS_THE_BRIG_ENABLED", "false");

    props.setProperty("KEYSTORE_PATH", keyStorePath);
    props.setProperty("KEYSTORE_PASSWORD", keyStorePassword);

    var context = new Context(Executors.newVirtualThreadPerTaskExecutor(), new Constants(props));
    context.setLogger(new Logger(context.getConstants(), context.getExecutorService(), "primary logger"));
    var minum = new FullSystem(context).start();
    var wf = minum.getWebFramework();

    wf.registerPreHandler((inputs) -> {
      var request = inputs.clientRequest();
      var csrfTokens = request.getHeaders().valueByKey("Java-Electron-Csrf-Token");

      if (csrfTokens != null && !csrfTokens.isEmpty() && csrfTokens.getFirst().equals(csrfToken)) {
        // ok
      } else {
        return Response.buildResponse(CODE_403_FORBIDDEN, Map.of(), "Java-Electron-Csrf-Token is invalid.");
      }

      ThrowingFunction<IRequest, IResponse> endpoint = inputs.endpoint();
      return endpoint.apply(inputs.clientRequest());
    });

    if (IS_LOCAL_DEV) {
      var httpClient = java.net.http.HttpClient.newHttpClient();
      wf.registerPartialPath(
        GET,
        "__webpack_hmr",
        request -> {
          var httpRequest = HttpRequest
            .newBuilder()
            .uri(URI.create("http://localhost:8090/__webpack_hmr"))
            .GET()
            .build();
          var response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofByteArray());

          return Response.buildResponse(
            StatusLine.StatusCode.CODE_200_OK,
            response.headers().map().entrySet().stream().collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, e -> String.join(",", e.getValue()))),
            response.body()
          );
        }
      );
      wf.registerPartialPath(
        GET,
        "assets/",
        request -> {
          var pattern = Pattern.compile("assets/(?<assetPath>.*$)");
          var path = request.getRequestLine().getPathDetails().getIsolatedPath();
          var matcher = pattern.matcher(path);

          if (!matcher.find()) {
            return Response.buildLeanResponse(StatusLine.StatusCode.CODE_404_NOT_FOUND);
          }

          var assetPath = matcher.group("assetPath");

          if (assetPath.startsWith("images/")) {
            return Response.buildResponse(
              StatusLine.StatusCode.CODE_200_OK,
              Map.of(
                "Content-Type", "image/png"
              ),
              MinumBuilder.class.getResourceAsStream("/assets/" + assetPath).readAllBytes()
            );
          }

          var httpRequest = HttpRequest
            .newBuilder()
            .uri(URI.create("http://localhost:8090/assets/" + assetPath))
            .GET()
            .build();
          var response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofByteArray());

          return Response.buildResponse(
            StatusLine.StatusCode.CODE_200_OK,
            response.headers().map().entrySet().stream().collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, e -> String.join(",", e.getValue()))),
            response.body()
          );
        }
      );
    } else {
      wf.registerPartialPath(
        GET,
        "assets/",
        request -> {
          var pattern = Pattern.compile("assets/(?<assetPath>.*$)");
          var path = request.getRequestLine().getPathDetails().getIsolatedPath();
          var matcher = pattern.matcher(path);

          if (!matcher.find()) {
            return Response.buildLeanResponse(StatusLine.StatusCode.CODE_404_NOT_FOUND);
          }

          var assetPath = matcher.group("assetPath");
          var resource = MinumBuilder.class.getResourceAsStream("/assets/" + assetPath);

          if (resource == null) {
            return Response.buildLeanResponse(StatusLine.StatusCode.CODE_404_NOT_FOUND);
          }

          return Response.buildResponse(
            StatusLine.StatusCode.CODE_200_OK,
            Map.of(
              "Content-Type", inferContentType(assetPath)
            ),
            resource.readAllBytes()
          );
        }
      );
    }

    Runtime.getRuntime().addShutdownHook(new Thread(minum::shutdown));

    // In SBT console, pressing Ctrl+C only sends SIGINT. Therefore, we have to trigger a shutdown when SIGINT occurs.
    sun.misc.Signal.handle(new sun.misc.Signal("INT"), sig -> {
      logger.info("Received SIGINT signal. Shutting down...");
      minum.shutdown();
    });

    return minum;
  }
}
