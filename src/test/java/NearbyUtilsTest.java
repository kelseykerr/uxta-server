import com.iuxta.uxta.UxtaUtils;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

/**
 * Created by kerrk on 12/3/16.
 */
public class NearbyUtilsTest {

    @Test
    public void testTransactionCode() {
        String code = UxtaUtils.getUniqueCode();
        assertTrue(code.length() == 6);
    }
}
