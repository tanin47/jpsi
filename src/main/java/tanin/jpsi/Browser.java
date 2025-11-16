package tanin.jpsi;

import dev.webview.Webview;
import tanin.ejwf.SelfSignedCertificate;

public class Browser {

  String url;
  SelfSignedCertificate cert;
  String csrfToken;

  public Browser(String url, SelfSignedCertificate cert, String csrfToken) {
    this.url = url;
    this.cert = cert;
    this.csrfToken = csrfToken;
  }

  public void run() throws InterruptedException {
    var wv = new Webview(true); // Can optionally be created with an AWT component to be painted on.
    wv.setInitScript(
      """
      var oldFetch = window.fetch;
      window.fetch = function () {
        // Some AJAX requests' urls start with `//` (e.g. helpscoutdocs.com). We need to exclude it. Otherwise,
        // we would hit CORS condition.
        if (arguments.length >= 1 && !!arguments[0].startsWith && arguments[0].startsWith('/') && !arguments[0].startsWith('//')) {
        } else {
          return oldFetch.apply(window, arguments);
        }

        var args = [...arguments]
        if (args.length === 1) {
          args.push({})
        }

        args[1].headers ||= {};

        if (args[1].method === 'POST' || args[1].method === "DELETE" || args[1].method === "PUT") {
          args[1].headers['Java-Electron-Csrf-Token'] = '%s';
        }

        return oldFetch.apply(window, args);
      };
        """.formatted(this.csrfToken)
    );

    wv.loadURL(this.url);


    wv.run(); // Run the webview event loop, the webview is fully disposed when this returns.
    wv.close(); // Free any resources allocated.
  }
}
