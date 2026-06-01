/*
 * Copyright (c) 2021 mobile.dev inc.
 * Android USB transport support by github.com/XRSec/
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package dadb.android.usb

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbInterface

internal object UsbConstants {

    const val INTERFACE_CLASS = 0xFF
    const val INTERFACE_SUBCLASS = 0x42
    const val INTERFACE_PROTOCOL = 0x01

    const val CONNECT_MAX_DATA = 15 * 1024
    const val MAX_PACKET_SIZE = 16 * 1024
    const val WRITE_TIMEOUT_MS = 5_000
    const val READ_TIMEOUT_MS = 1_000
    const val READ_IDLE_TIMEOUT_MS = 30_000

    fun isAdb(device: UsbDevice): Boolean {
        for (index in 0 until device.interfaceCount) {
            if (isAdb(device.getInterface(index))) {
                return true
            }
        }
        return false
    }

    fun isAdb(usbInterface: UsbInterface): Boolean {
        return usbInterface.interfaceClass == INTERFACE_CLASS &&
            usbInterface.interfaceSubclass == INTERFACE_SUBCLASS &&
            usbInterface.interfaceProtocol == INTERFACE_PROTOCOL
    }
}
