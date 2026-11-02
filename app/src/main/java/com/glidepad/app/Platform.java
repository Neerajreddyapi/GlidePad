package com.glidepad.app;

/**
 * What Glide Pad is connected to. The trackpad, typing and media keys work the same everywhere;
 * this decides the shortcuts, gestures, zoom keys, scroll direction, key names and pairing steps.
 */
final class Platform {

    static final int MAC = 0, WINDOWS = 1, CHROMEBOOK = 2, IPAD = 3, LINUX = 4, TV = 5, OTHER = 6;

    /** One key combination (or media key) with the label shown in the app. */
    static final class Action {
        final String label, keys;
        final int mods, code, usage;

        private Action(String label, String keys, int mods, int code, int usage) {
            this.label = label;
            this.keys = keys;
            this.mods = mods;
            this.code = code;
            this.usage = usage;
        }

        static Action key(String label, String keys, int mods, int code) { return new Action(label, keys, mods, code, 0); }

        static Action media(String label, int usage) { return new Action(label, "", 0, 0, usage); }

        void send(HidManager hid) {
            if (usage != 0) hid.media(usage); else hid.key(mods, code);
        }
    }

    final int id;
    /** "Mac", "Windows PC"… and the short form used in sentences ("your Mac", "your PC"). */
    final String name, noun, subtitle;
    final int icon;
    /** True when the host already flips the wheel for "natural scrolling" (macOS, iPadOS). */
    final boolean naturalScroll;
    /** Modifier for pinch to zoom (+ / −), or 0 when zooming isn't a thing there. */
    final int zoomMods;
    /** Four-finger gestures; null when there's no reliable shortcut on that system. */
    final Action swipeUp, swipeDown, swipeLeft, swipeRight, spread;
    final Action[] shortcuts;
    /** Modifier keys for the key row, in the order that system's users expect. */
    final String[] modLabels;
    final int[] modBits;
    /** Pairing steps 2 and 3 (HTML); "%s" is replaced with the phone's name. */
    final String pairStep2, pairStep3;

    private Platform(int id, String name, String noun, String subtitle, int icon, boolean naturalScroll, int zoomMods,
                     Action swipeUp, Action swipeDown, Action swipeLeft, Action swipeRight, Action spread,
                     Action[] shortcuts, String[] modLabels, int[] modBits, String pairStep2, String pairStep3) {
        this.id = id;
        this.name = name;
        this.noun = noun;
        this.subtitle = subtitle;
        this.icon = icon;
        this.naturalScroll = naturalScroll;
        this.zoomMods = zoomMods;
        this.swipeUp = swipeUp;
        this.swipeDown = swipeDown;
        this.swipeLeft = swipeLeft;
        this.swipeRight = swipeRight;
        this.spread = spread;
        this.shortcuts = shortcuts;
        this.modLabels = modLabels;
        this.modBits = modBits;
        this.pairStep2 = pairStep2;
        this.pairStep3 = pairStep3;
    }

    boolean hasGestures() { return swipeUp != null || swipeLeft != null || spread != null; }

    // ------------------------------------------------------------------ data

    private static final int C = Keys.CTRL, S = Keys.SHIFT, A = Keys.ALT, G = Keys.CMD; // G = ⌘ / Win / Search / Super

    private static final String[] MAC_MODS = {"⌘", "⌥", "⌃", "⇧"};
    private static final int[] MAC_BITS = {G, A, C, S};
    private static final String[] PC_MODS = {"Ctrl", "Alt", "Win", "Shift"};
    private static final String[] CHROME_MODS = {"Ctrl", "Alt", "Search", "Shift"};
    private static final String[] LINUX_MODS = {"Ctrl", "Alt", "Super", "Shift"};
    private static final int[] PC_BITS = {C, A, G, S};

    /** Everyday shortcuts that work on Windows, Linux and most other systems. */
    private static Action[] ctrlBasics(Action first, Action last) {
        return new Action[]{
                first,
                Action.key("Switch app", "Alt Tab", A, Keys.TAB),
                Action.key("Copy", "Ctrl C", C, Keys.KEY_C),
                Action.key("Paste", "Ctrl V", C, Keys.KEY_V),
                Action.key("Undo", "Ctrl Z", C, Keys.KEY_Z),
                Action.key("Cut", "Ctrl X", C, Keys.KEY_X),
                Action.key("Select all", "Ctrl A", C, Keys.KEY_A),
                Action.key("Find", "Ctrl F", C, Keys.KEY_F),
                Action.key("New tab", "Ctrl T", C, Keys.KEY_T),
                Action.key("Close tab", "Ctrl W", C, Keys.KEY_W),
                Action.key("Refresh", "Ctrl R", C, Keys.KEY_R),
                last,
        };
    }

    private static final Platform[] ALL = {
            new Platform(MAC, "Mac", "Mac", "MacBook, iMac, Mac mini", R.drawable.ic_dev_laptop, true, G,
                    Action.key("Mission Control", "⌃ ↑", C, Keys.UP),
                    Action.key("App Exposé", "⌃ ↓", C, Keys.DOWN),
                    Action.key("Next desktop →", "⌃ →", C, Keys.RIGHT),
                    Action.key("← Previous desktop", "⌃ ←", C, Keys.LEFT),
                    Action.key("Show Desktop", "F11", 0, Keys.F11),
                    new Action[]{
                            Action.key("Spotlight", "⌘ Space", G, Keys.SPACE),
                            Action.key("Mission Control", "⌃ ↑", C, Keys.UP),
                            Action.key("Switch app", "⌘ Tab", G, Keys.TAB),
                            Action.key("Copy", "⌘ C", G, Keys.KEY_C),
                            Action.key("Paste", "⌘ V", G, Keys.KEY_V),
                            Action.key("Undo", "⌘ Z", G, Keys.KEY_Z),
                            Action.key("Desktop ←", "⌃ ←", C, Keys.LEFT),
                            Action.key("Desktop →", "⌃ →", C, Keys.RIGHT),
                            Action.key("App Exposé", "⌃ ↓", C, Keys.DOWN),
                            Action.key("Screenshot", "⌘ ⇧ 4", G | S, Keys.KEY_4),
                            Action.key("Close window", "⌘ W", G, Keys.KEY_W),
                            Action.key("Lock screen", "⌃ ⌘ Q", C | G, Keys.KEY_Q),
                    }, MAC_MODS, MAC_BITS,
                    "On the Mac, open <b>System Settings → Bluetooth</b>.",
                    "Next to <b>%s</b>, click <b>Connect</b> and accept the code on both screens."),

            new Platform(WINDOWS, "Windows PC", "PC", "Laptop or desktop", R.drawable.ic_dev_desktop, false, C,
                    Action.key("Task View", "Win Tab", G, Keys.TAB),
                    Action.key("Show desktop", "Win D", G, Keys.KEY_D),
                    Action.key("Next desktop →", "Ctrl Win →", C | G, Keys.RIGHT),
                    Action.key("← Previous desktop", "Ctrl Win ←", C | G, Keys.LEFT),
                    Action.key("Show desktop", "Win D", G, Keys.KEY_D),
                    new Action[]{
                            Action.key("Start", "Win", G, 0),
                            Action.key("Task View", "Win Tab", G, Keys.TAB),
                            Action.key("Switch app", "Alt Tab", A, Keys.TAB),
                            Action.key("Copy", "Ctrl C", C, Keys.KEY_C),
                            Action.key("Paste", "Ctrl V", C, Keys.KEY_V),
                            Action.key("Undo", "Ctrl Z", C, Keys.KEY_Z),
                            Action.key("Desktop ←", "Ctrl Win ←", C | G, Keys.LEFT),
                            Action.key("Desktop →", "Ctrl Win →", C | G, Keys.RIGHT),
                            Action.key("Show desktop", "Win D", G, Keys.KEY_D),
                            Action.key("Screenshot", "Win Shift S", G | S, Keys.KEY_S),
                            Action.key("Close window", "Alt F4", A, Keys.F4),
                            Action.key("Lock screen", "Win L", G, Keys.KEY_L),
                    }, PC_MODS, PC_BITS,
                    "On the PC, open <b>Settings → Bluetooth &amp; devices → Add device → Bluetooth</b>.",
                    "Choose <b>%s</b> and accept the code on both screens."),

            new Platform(CHROMEBOOK, "Chromebook", "Chromebook", "ChromeOS", R.drawable.ic_dev_chromebook, false, C,
                    null, null, null, null, null,
                    ctrlBasics(Action.key("Launcher", "Search", G, 0), Action.key("Lock screen", "Search L", G, Keys.KEY_L)),
                    CHROME_MODS, PC_BITS,
                    "On the Chromebook, open <b>Settings → Bluetooth → Pair new device</b>.",
                    "Choose <b>%s</b> and accept the code on both screens."),

            new Platform(IPAD, "iPad", "iPad", "iPadOS", R.drawable.ic_dev_tablet, true, G,
                    null, null, null, null, null,
                    new Action[]{
                            Action.key("Home", "⌘ H", G, Keys.KEY_H),
                            Action.key("Switch app", "⌘ Tab", G, Keys.TAB),
                            Action.key("Search", "⌘ Space", G, Keys.SPACE),
                            Action.key("Copy", "⌘ C", G, Keys.KEY_C),
                            Action.key("Paste", "⌘ V", G, Keys.KEY_V),
                            Action.key("Undo", "⌘ Z", G, Keys.KEY_Z),
                            Action.key("Cut", "⌘ X", G, Keys.KEY_X),
                            Action.key("Select all", "⌘ A", G, Keys.KEY_A),
                            Action.key("Find", "⌘ F", G, Keys.KEY_F),
                            Action.key("New tab", "⌘ T", G, Keys.KEY_T),
                            Action.key("Close tab", "⌘ W", G, Keys.KEY_W),
                            Action.key("Screenshot", "⌘ ⇧ 3", G | S, Keys.KEY_3),
                    }, MAC_MODS, MAC_BITS,
                    "On the iPad, open <b>Settings → Bluetooth</b>.",
                    "Under Other Devices, tap <b>%s</b> and accept the code on both screens."),

            new Platform(LINUX, "Linux", "computer", "Ubuntu, Fedora and more", R.drawable.ic_dev_terminal, false, C,
                    null, null, null, null, null,
                    ctrlBasics(Action.key("Activities", "Super", G, 0), Action.key("Close window", "Alt F4", A, Keys.F4)),
                    LINUX_MODS, PC_BITS,
                    "On the computer, open <b>Settings → Bluetooth</b> and make sure Bluetooth is on.",
                    "Choose <b>%s</b> and accept the code on both screens."),

            new Platform(TV, "Smart TV", "TV", "Smart TVs and smart monitors", R.drawable.ic_dev_tv, false, 0,
                    null, null, null, null, null,
                    new Action[]{
                            Action.media("Home", Keys.AC_HOME),
                            Action.media("Back", Keys.AC_BACK),
                            Action.key("OK", "Enter", 0, Keys.ENTER),
                            Action.key("↑", "Up", 0, Keys.UP),
                            Action.key("↓", "Down", 0, Keys.DOWN),
                            Action.media("Search", Keys.AC_SEARCH),
                            Action.key("←", "Left", 0, Keys.LEFT),
                            Action.key("→", "Right", 0, Keys.RIGHT),
                            Action.media("Play / pause", Keys.PLAY_PAUSE),
                            Action.media("Volume −", Keys.VOLUME_DOWN),
                            Action.media("Volume +", Keys.VOLUME_UP),
                            Action.media("Mute", Keys.MUTE),
                    }, LINUX_MODS, PC_BITS,
                    "On the TV, open <b>Settings → Remotes &amp; accessories</b> (or <b>Bluetooth</b>) and choose <b>Add accessory</b>.",
                    "Choose <b>%s</b> and accept the code on both screens."),

            new Platform(OTHER, "Other", "device", "Anything with Bluetooth", R.drawable.ic_dev_more, false, C,
                    null, null, null, null, null,
                    ctrlBasics(Action.key("Escape", "Esc", 0, Keys.ESC), Action.key("Close window", "Alt F4", A, Keys.F4)),
                    LINUX_MODS, PC_BITS,
                    "On the device, open its <b>Bluetooth</b> settings and search for new devices.",
                    "Choose <b>%s</b> and accept the code on both screens."),
    };

    static Platform[] all() { return ALL; }

    /** The chosen device, or Mac when nothing has been chosen yet. */
    static Platform get(int id) { return id >= 0 && id < ALL.length ? ALL[id] : ALL[MAC]; }
}
