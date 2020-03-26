package dk.es.br.dibs;

import static org.testng.Assert.*;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Map;

/**
 *
 * @author osa
 */
public class DibsClientTest {
  
  public DibsClientTest() {
  }

  @BeforeMethod
  public void setUpMethod() throws Exception {
  }

  @AfterMethod
  public void tearDownMethod() throws Exception {
  }

  @Test
  public void testMD5() {
    String k1 = "K+NBa~?KS6~x4cAx3oJ_3!c#M.c9f8)k";
    String k2 = "wBz{8igqJGzi@?*16bIx!t5_.d$n#A{k";
    String res1 = DibsClient.MD5(k2 + DibsClient.MD5(k1 + "transact=10117&amount=9995&currency=208"));
    assertEquals(res1, "0d15b6dbdb0ecfd11fcd9bf99a55f529");

    String res2 = DibsClient.MD5(k2 + DibsClient.MD5(k1 + "merchant=4259425&orderid=F2487845&currency=208&amount=30000"));
    assertEquals(res2, "d7d0716f705d66b7f333f61e2b705f9c");

    String res3 = DibsClient.MD5(k2 + DibsClient.MD5(k1 + "transact=1207851850&amount=30000&currency=208"));
    assertEquals(res3, "bb510401c0886d9b939f71e6c43f7984");
  }

  @Test
  public void testParseResponseOnTestCardResponse()
  {
    String response = "status=ACCEPTED&transact=2718314359&authkey=a83ac622c10dc2a100eee91997a5a70b&cardtype=V-DK&approvalcode=123456&orderid=F8087028&paytype=V-DK&acquirer=TEST&merchantid=111111&=&capturenow=yes&currency=208&fullreply=yes&md5key=4e27b9c9eaafdc27669ebee92ea3f790&merchant=4259425&test=yes&textreply=yes&ticket=2718313871&uniqueoid=yes&amount=200000&totalamount=200000&cardcountry=DK&cardprefix=457110&cardnomask=XXXXXXXXXXXX0000&cardexpdate=2406&cardtypeCD=unknown&surchargeregion=unknown&privatebusiness=unknown";
    Map<String, String> parsed = DibsClient.parseResponse(response);
    assertEquals(parsed.get("status"), "ACCEPTED");
  }
}
