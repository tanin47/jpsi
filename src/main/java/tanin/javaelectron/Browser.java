package tanin.javaelectron;

import tanin.ejwf.SelfSignedCertificate;

import static tanin.javaelectron.WebviewNative.N;

public class Browser {

  String url;
  SelfSignedCertificate cert;

  public Browser(String url, SelfSignedCertificate cert) {
    this.url = url;
    this.cert = cert;
  }

  public void run() throws InterruptedException {
    var pointer = N.webview_create(true, null);
    N.webview_navigate(pointer, this.url);

    N.webview_run(pointer);
    N.webview_destroy(pointer);
    N.webview_terminate(pointer);
  }
}
