package com.impulsecontrol.lend;

import org.apache.commons.codec.binary.Base32;

import java.nio.ByteBuffer;
import java.util.UUID;

/**
 * Created by kerrk on 12/3/16.
 */
public class NearbyUtils {

    public static String getUniqueCode() {
        UUID uuid = UUID.randomUUID();
        ByteBuffer byteBuffer = ByteBuffer.wrap(new byte[16]);
        byteBuffer.putLong(uuid.getMostSignificantBits());
        byteBuffer.putLong(uuid.getLeastSignificantBits());
        Base32 BASE32 = new Base32();
        String code = BASE32.encodeAsString(byteBuffer.array()).replaceAll("=", "");
        code = code.substring(0, 15);
        return code.replaceAll("(.{5})(?!$)", "$1-");
    }
}
