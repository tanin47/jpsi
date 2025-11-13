module tanin.jpsi {
  // Java platform modules
  requires java.desktop;      // Swing/AWT for the Browser UI
  requires java.logging;      // java.util.logging used in Main
  requires org.bouncycastle.lts.pkix;
  requires org.bouncycastle.lts.util;
  requires org.bouncycastle.lts.prov;
  requires jdk.unsupported;
  requires java.net.http;
  requires jcef;
  requires tanin.jpsi;

  // Export our public APIs/packages
  exports tanin.jpsi;
  exports tanin.ejwf;
}
