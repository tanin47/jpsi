package tanin.javaelectron;

import com.sun.jna.Native;
import com.sun.jna.ptr.PointerByReference;
import tanin.ejwf.SelfSignedCertificate;

import javax.swing.*;

import java.awt.*;
import java.util.logging.Logger;

import static tanin.javaelectron.WebviewNative.N;

public class Browser {
  private static final Logger logger = Logger.getLogger(Browser.class.getName());

  String url;
  SelfSignedCertificate cert;

  public Browser(String url, SelfSignedCertificate cert) {
    this.url = url;
    this.cert = cert;
  }

  public void run() throws InterruptedException {
    var frame = new JFrame();

    var canvas = new WebViewCanvas();

    frame.getContentPane().add(canvas, BorderLayout.CENTER);

    frame.setTitle("My Webview App");
    frame.setSize(800, 600);
    frame.setVisible(true);
    logger.info("Make JFrame Visible");
  }

  static class WebViewCanvas extends Canvas {
    boolean isInitialized = false;
    long webview = -1;

    private Dimension lastSize = null;

    WebViewCanvas() {
      setBackground(Color.BLACK);
      logger.info("Instantiate the canvas");
    }

    @Override
    public void paint(Graphics g) {
      logger.info("Paint");
      Dimension size = this.getSize();

      if (!size.equals(this.lastSize)) {
        this.lastSize = size;

        if (this.webview != -1) {
          this.updateSize();
        }
      }

      if (!isInitialized) {
        isInitialized = true;
        webview = N.webview_create(true, new PointerByReference(Native.getComponentPointer(this)));

        // We need to create the webview off of the swing thread.
        Thread t = new Thread(() -> {

          logger.info("Webview runs");
          N.webview_run(webview);
          N.webview_destroy(webview);
        });

        t.setDaemon(false);
        t.setName("AWTWebview RunAsync Thread - #" + this.hashCode());
        t.start();
        logger.info("Webview thread started");
      }
    }

    private void updateSize() {
      int width = this.lastSize.width;
      int height = this.lastSize.height;

      N.webview_set_size(webview, width, height, WebviewNative.WV_HINT_FIXED);
    }
  }
}
