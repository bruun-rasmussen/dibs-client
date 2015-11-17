package dk.es.br.dibs;

import static org.testng.Assert.*;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

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
    String res = DibsClient.MD5(k2 + DibsClient.MD5(k1 + "transact=10117&amount=9995&currency=208"));
    assertEquals(res, "0d15b6dbdb0ecfd11fcd9bf99a55f529");
  }
  
}
