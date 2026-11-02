package com.glidepad.app;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;

/** What a Bluetooth device is, from the class it advertises: picks the icon and label we show. */
enum DeviceKind {
    LAPTOP("Laptop", R.drawable.ic_dev_laptop, true),
    DESKTOP("Computer", R.drawable.ic_dev_desktop, true),
    TABLET("Tablet", R.drawable.ic_dev_tablet, true),
    TV("TV", R.drawable.ic_dev_tv, true),
    TV_BOX("TV box", R.drawable.ic_dev_tv, true),
    COMPUTER("Computer", R.drawable.ic_dev_laptop, true),
    UNKNOWN("Bluetooth device", R.drawable.ic_bluetooth, true),
    PHONE("Phone", R.drawable.ic_dev_phone, false),
    AUDIO("Audio", R.drawable.ic_dev_headphones, false),
    OTHER("Accessory", R.drawable.ic_bluetooth, false);

    final String label;
    final int icon;
    /** Things that can use a keyboard and mouse. Phones, headphones and watches can't. */
    final boolean canHost;

    DeviceKind(String label, int icon, boolean canHost) {
        this.label = label;
        this.icon = icon;
        this.canHost = canHost;
    }

    @SuppressLint("MissingPermission")
    static DeviceKind of(BluetoothDevice d) {
        BluetoothClass c = d == null ? null : d.getBluetoothClass();
        if (c == null) return UNKNOWN;
        int cls = c.getDeviceClass();
        switch (c.getMajorDeviceClass()) {
            case BluetoothClass.Device.Major.COMPUTER:
                if (cls == BluetoothClass.Device.COMPUTER_LAPTOP) return LAPTOP;
                if (cls == BluetoothClass.Device.COMPUTER_DESKTOP || cls == BluetoothClass.Device.COMPUTER_SERVER) return DESKTOP;
                if (cls == BluetoothClass.Device.COMPUTER_HANDHELD_PC_PDA || cls == BluetoothClass.Device.COMPUTER_PALM_SIZE_PC_PDA)
                    return TABLET;
                return COMPUTER;
            case BluetoothClass.Device.Major.AUDIO_VIDEO:
                if (cls == BluetoothClass.Device.AUDIO_VIDEO_VIDEO_DISPLAY_AND_LOUDSPEAKER
                        || cls == BluetoothClass.Device.AUDIO_VIDEO_VIDEO_MONITOR) return TV;
                if (cls == BluetoothClass.Device.AUDIO_VIDEO_SET_TOP_BOX) return TV_BOX;
                return AUDIO;
            case BluetoothClass.Device.Major.PHONE:
                return PHONE;
            case BluetoothClass.Device.Major.MISC:
            case BluetoothClass.Device.Major.UNCATEGORIZED:
                return UNKNOWN;
            default:
                return OTHER;
        }
    }
}
