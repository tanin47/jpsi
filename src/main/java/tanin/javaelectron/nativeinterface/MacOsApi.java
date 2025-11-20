package tanin.javaelectron.nativeinterface;

import com.sun.jna.Library;
import com.sun.jna.Native;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Collections;
import java.util.logging.Logger;

public interface MacOsApi extends Base {
  static final Logger logger = Logger.getLogger(WebviewNative.class.getName());
  static final MacOsApi N = runSetup();

  private static MacOsApi runSetup() {
     Base.prepareLib("/libMacOsApi.dylib");

    return Native.load(
      "MacOsApi",
      MacOsApi.class,
      Collections.singletonMap(Library.OPTION_STRING_ENCODING, "UTF-8")
    );
  }

  void setupMenu();
}
