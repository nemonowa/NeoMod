package neo.menu.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Mod Menu 全体で共有する定数。他のどのクラスにも依存しない最下層。 */
public final class ModManagerInfo {
    public static final String MOD_ID = "modmenu";
    public static final Logger LOGGER = LoggerFactory.getLogger("Mod Manager");

    private ModManagerInfo() {}
}
