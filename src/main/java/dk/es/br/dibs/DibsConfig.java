package dk.es.br.dibs;

/**
 *
 * @author osa
 */
public interface DibsConfig {

    String getMerchantId();
    String getDibsUser();
    String getDibsPassword();
    String getMd5K1();
    String getMd5K2();

    boolean isTesting();
    
}
