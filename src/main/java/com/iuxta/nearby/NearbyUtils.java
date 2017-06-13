package com.iuxta.nearby;

import com.iuxta.nearby.model.User;
import org.apache.commons.codec.binary.Base32;

import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.util.Currency;
import java.util.UUID;

/**
 * Created by kerrk on 12/3/16.
 */
public class NearbyUtils {

    public static final String GOOGLE_AUTH_METHOD = "google";
    public static final String FB_AUTH_METHOD = "facebook";
    public static final Double MINIMUM_OFFER_PRICE = 0.5;
    public static final Currency USD = Currency.getInstance("USD");
    public static final RoundingMode DEFAULT_ROUNDING = RoundingMode.HALF_EVEN;
    public static final int MAX_OPEN_REQUESTS = 10;
    public static final int MAX_OPEN_RESPONSES = 5;
    public static final int DEFAULT_LIMIT = 20;
    public static final int MAX_LIMIT = 20;

    //6 character string
    public static String getUniqueCode() {
        UUID uuid = UUID.randomUUID();
        ByteBuffer byteBuffer = ByteBuffer.wrap(new byte[16]);
        byteBuffer.putLong(uuid.getMostSignificantBits());
        byteBuffer.putLong(uuid.getLeastSignificantBits());
        Base32 BASE32 = new Base32();
        String code = BASE32.encodeAsString(byteBuffer.array()).replaceAll("=", "");
        return code.substring(0, 6);
        //return code.replaceAll("(.{5})(?!$)", "$1-");
    }

    public static String getUserIdString(User user) {
        return " [" + user.getName() + " - " + user.getId() + "] ";
    }
}
