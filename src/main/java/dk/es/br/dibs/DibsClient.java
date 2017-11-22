package dk.es.br.dibs;

import java.io.*;
import java.net.*;
import java.security.Security;

import com.sun.net.ssl.internal.ssl.Provider;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Currency;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.StringTokenizer;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class serves as a payment service interface to the DIBS server. It has
 * methods for creating and managing accounts and transfer money.
 *
 * TODO: * MD5 Checksums * Account names for Bredgade, Havnen,... ? * Passing
 * customer IP along - use "suspect" feature * Enforce currency. Account type
 * check * Use the "ordertext" parameter * Utilise refund endpoint, documented at
 * http://tech.dibspayment.com/D2/API/Payment_functions/refundcgi
 *
 * @author Bruun Rasmussen Kunstauktioner
 * @since 9. september 2004
 * @version $Id: Dibs.java 18573 2013-12-19 13:08:33Z o.sandum $
 */
public class DibsClient
{
  private final static Logger LOG = LoggerFactory.getLogger(DibsClient.class);

  static
  {
    try
    {
      System.setProperty("java.protocol.handler.pkgs", "com.sun.net.ssl.internal.www.protocol");
      Provider provider = new Provider();
      Security.addProvider(provider);
      LOG.info("Added com.sun.net.ssl.internal.www.protocol security provider");
    }
    catch (Exception e)
    {
      LOG.error("Error setting protocol handlers needed by DIBS: " + e);
    }
  }

  private final DibsConfig cfg;

  public DibsClient(DibsConfig cfg)
  {
      this.cfg = cfg;
  }

  /**
   * Deletes the specified account from the DIBS system. The account template
   * MUST define the merchant and the account id properties
   *
   * @param accountId the account to delete
   * @throws DibsException if the account does not exist or for some reason cannot be deleted
   */
  public void deleteAccount(String accountId)
    throws DibsException
  {
    // First fill out the message to dibs
    Map msg = new HashMap();
    msg.put("merchant", getMerchantId());
    msg.put("ticket", accountId);

    // Query the DIBS server
    Map result = post("/cgi-adm/delticket.cgi", msg, true);
    String status = (String) result.get("status");
    if (status == null || !status.equalsIgnoreCase("ACCEPTED"))
      throw new DibsException("'" + accountId + "': failed to delete account: " + result.get("message") + " (" + result.get("reason") + ")");
  }

  private enum Iso4217 {
    DKK("208"), EUR("978"), USD("840"), GBP("826"),
    SEK("752"), AUD("036"), CAD("124"), ISK("352"),
    JPY("392"), NZD("554"), NOK("578"), CHF("756"),
    TRL("792");

    private final String code;

    Iso4217(String code) {
      this.code = code;
    }
  }
  
  public static String codeOf(Currency currency) {
    /* replace with currency.getNumericCode() (as of java 1.7) sometime... */
    Iso4217 dc = Iso4217.valueOf(currency.getCurrencyCode());
    return dc.code;
  }
  
  /**
   * Checks the validity of the specified account in the DIBS system. This is
   * done by attempting to authorize a small transaction, and then immediately cancel
   * the authorization.
   *
   * @param accountId the account to check
   * @param cents the amount to test authorization against
   * @param currency the currency to test authorization against
   * @return "ok" if there are no problems. Or the response
   */
  public DibsResponse validateCardSubscription(String accountId, int cents, Currency currency)
    throws DibsException
  {    
    // First fill out the message to dibs - authorize a 1kr transfer
    Map params = new HashMap();

    params.put("merchant", getMerchantId());
    params.put("ticket", accountId);
    params.put("orderid", "checking-account");
    params.put("amount", String.valueOf(cents));
    params.put("currency", codeOf(currency));

    // Query the DIBS server
    Map result = post("/cgi-ssl/ticket_auth.cgi", params, false);

    String status = (String)result.get("status");

    if ("ACCEPTED".equalsIgnoreCase(status))
    {
      // Checked out fine. Now cancel the authorization:
      String transact = (String)result.get("transact");

      // Change the status to be canceled
      params = new HashMap();
      params.put("merchant", getMerchantId());
      params.put("transact", transact);

      try
      {
        post("/cgi-adm/cancel.cgi", params, true);
      }
      catch (Exception ex)
      {
        LOG.error("Validate Account: Exception trying to cancel authorization " + transact, ex);
      }

      LOG.info(accountId + " checked positive");

      return new CheckAccountResponse(true, result);
    }

    String reason = (String)result.get("reason");
    String message = (String)result.get("message");

    // Presume auth failed. Need to see if the cardholder is to blame
    switch (new Integer(reason))
    {
      case 1: // Communication problems
      case 2: // Error in the parameters sent to the DIBS server
      case 3: // Error at the acquirer
        throw new RuntimeException("Account validation failed, " + result);
    }

    LOG.info(accountId + " checked negative (" + reason + ": " + message + ")");
    return new CheckAccountResponse(false, result);
  }

  private Map post(String path, Map params, boolean auth)
          throws DibsException
  {
    long t1 = System.currentTimeMillis();

    String query = formatQuery(params);
    URL url = dibsUrl(path);
    try {
      String response = _post(url, query, auth);
      Map res = parseResponse(response);
      LOG.info(path + "["+params+"] : " + res);
      return res;
    }
    finally {
      long t2 = System.currentTimeMillis();
      LOG.info("DIBS call:" + path + " " + query + ": " + (t2-t1) + "ms");
    }
  }

  private static URL dibsUrl(String path) {
      try {
          return new URL("https://payment.architrade.com" + path);
      } catch (MalformedURLException ex) {
          throw new IllegalArgumentException(path, ex);
      }
  }

  /**
   * This method will authorize and deduct the specified amount of money from
   * the specified account
   *
   * @param accountId the account to check
   * @param orderId the unique order id
   * @param amount the amount of money to deduct
   * @param chargeCardFee whether to charge card fee to the given card account
   * @return the transaction id
   */
  public TransactionInfo withdraw(String accountId,
                                  String orderId,
                                  BigDecimal amount,
                                  Currency currency,
                                  boolean chargeCardFee)
    throws DibsException
  {
    long cents = Math.round(amount.doubleValue() * 100.0);

    // Sanity checks
    if (StringUtils.isEmpty(accountId))
      throw new IllegalArgumentException("Account id missing");
    if (StringUtils.isEmpty(orderId))
      throw new IllegalArgumentException("Order id missing");
    if (cents < 0)
      throw new DibsException("Cannot withdraw kr: " + amount);

    long t1 = System.currentTimeMillis();
    LOG.info("Withdraw " + amount + " from card account " + accountId + ", orderId " + orderId);
    TransactionInfo transaction = withdrawCents(accountId, orderId, cents, currency, chargeCardFee);
    Long transactionId = transaction.transactionId();
    BigDecimal feeAmount = transaction.feeAmount();
    long t2 = System.currentTimeMillis();
    LOG.info("Withdrew " + amount + " from card account " + accountId + ", orderId " + orderId + ": transaction " + transactionId + ", fee reported by Dibs: " + feeAmount + " (" + (t2-t1) + "ms)");
    return transaction;
  }

  private TransactionInfo withdrawCents(String accountId,
                                        String orderId,
                                        long cents,
                                        Currency currency,
                                        boolean chargeCardFee)
    throws DibsException
  {
    // First fill out the message to dibs
    Map msg = new HashMap();
    msg.put("merchant", getMerchantId());
    msg.put("ticket", accountId);
    msg.put("orderid", orderId);
    msg.put("amount", cents);
    msg.put("currency", codeOf(currency));
    msg.put("capturenow", "yes");
    msg.put("uniqueoid", "yes");

    // cf. http://tech.dibspayment.com/D2/FlexWin/API/MD5
    String md5key = md5of("merchant=" + getMerchantId() + "&orderid=" + orderId + "&ticket=" + accountId + "&currency=" + codeOf(currency) + "&amount=" + cents);
    msg.put("md5key", md5key);

    if (isTesting())
      msg.put("test", "yes");

    if (chargeCardFee)
      msg.put("calcfee", "yes");

    // Query the DIBS server
    Map result = post("/cgi-ssl/ticket_auth.cgi", msg, false);

    String status = (String)result.get("status");
    String message = (String)result.get("message");
    if (!"ACCEPTED".equals(status))
      throw new DibsException("Withdrawal " + status + ": " + message, (String)result.get("reason"), (String)result.get("actioncode"));

    String transact = (String)result.get("transact");
    if (StringUtils.isEmpty(transact))
      throw new DibsException("Withdrawal " + status + " without transaction: " + message, (String)result.get("reason"), (String)result.get("actioncode"));

    String reportedFee = (String)result.get("fee");
    Long feeCents = reportedFee != null
                  ? Long.valueOf(reportedFee)
                  : new Long(0);

    final BigDecimal feeAmount = new BigDecimal(feeCents).scaleByPowerOfTen(-2);
    final Long transactionId = Long.valueOf(transact);

    // TODO: From DIBS, does severity != null => suspect is true ?
    String suspect = (String)result.get("suspect");
    final Boolean isSuspect = suspect != null
                      ? Boolean.valueOf(suspect)
                      : false;

    String severity = (String)result.get("severity");
    final Integer suspectSeverity = suspect != null
                            ? Integer.valueOf(severity)
                            : null;

    return new TransactionInfo()
    {
      @Override
      public Long transactionId()
      {
        return transactionId;
      }

      @Override
      public BigDecimal feeAmount()
      {
        return feeAmount;
      }

      @Override
      public Boolean suspect()
      {
        return isSuspect;
      }

      @Override
      public Integer severity()
      {
        return suspectSeverity;
      }
    };
  }

  /**
   * Looks up the card type for the given transaction. List of card type can be
   * found here: http://www.dibs.dk/136.0.html
   *
   * @param transactionId the transaction to determine the card type for
   */
  public String getCardType(Long transactionId)
          throws DibsException
  {
    // First fill out the message to dibs
    Map msg = new HashMap();
    msg.put("merchant", getMerchantId());
    msg.put("transact", transactionId);

    // Query the DIBS server
    Map res = post("/cardtype.pml", msg, true);

    // Check that the transaction was executed properly.
    // If the card type is not recognized, "0" is returned
    String arg1 = (String)res.get("1");

    if ("0".equals(arg1)) {
      LOG.info(transactionId + ": card type not recognized");
      return null;
    }

    // The reply is the card type
    return arg1;
  }

  private String getMerchantId()
  {
    return cfg.getMerchantId();
  }

  private String basicAuth()
  {
    String dibsUser = cfg.getDibsUser();
    String dibsPass = cfg.getDibsPassword();
    String userpass = dibsUser + ":" + dibsPass;
    return "Basic " + new String(Base64.encodeBase64(userpass.getBytes()));
  }

  /**
   * Posts a request to the DIBS server. Solemnly stolen from DIBS' sample
   * DeltapayHTTP class. Small changes implemented.
   *
   * @param url the server
   * @param message the parameters
   * @param auth should we use basic authentication
   * @return the result
   */
  private String _post(URL url, String message, boolean auth)
    throws DibsException
  {
    PrintWriter wrt;
    URLConnection conn;
    try {
      conn = url.openConnection();
      conn.setDoOutput(true);
      conn.setUseCaches(false);

      // DO:
      // conn.setConnectTimeout(...);
      // conn.setReadTimeout(....);

      if (auth)
        conn.setRequestProperty("Authorization", basicAuth());

      OutputStream os = conn.getOutputStream();
   
      wrt = new PrintWriter(os);    
    }
    catch (IOException ex) {
      LOG.error(url + ": failed to connect", ex);
      throw new DibsException("failed to connect", ex);
    }
    
    try {
      wrt.println(message);
    }
    finally {
      wrt.close();
    }

    StringBuilder res = new StringBuilder();
    try {
      BufferedReader rdr = new BufferedReader(new InputStreamReader(conn.getInputStream()));
      try
      {
        String line;
        while ((line = rdr.readLine()) != null)
          res.append(line);
      }
      finally
      {
        rdr.close();
      }
    }
    catch (IOException ex) {
      LOG.error(url + "[" + message + "]: failed to get response", ex);      
      throw new DibsException("failed to get response", ex);      
    }
    return res.toString();
  }

  /**
   * Parses the response sent from the DIBS server and converts it into a param
   * name value map.
   *
   * @param s the response received from the server
   * @return the result map
   */
  private static Map parseResponse(String s)
  {
    Map res = new HashMap();
    StringTokenizer st = new StringTokenizer(s, "&");
    while (st.hasMoreTokens())
    {
      String s1 = st.nextToken();
      StringTokenizer st2 = new StringTokenizer(s1, "=");
      String key = st2.countTokens() == 2 ? st2.nextToken().toLowerCase() : String.valueOf(res.size() + 1);
      String val = urlDecodeUTF8(st2.nextToken());
      res.put(key, val);
    }
    return res;
  }

  private static String formatQuery(Map params)
  {
    StringBuilder msg = new StringBuilder();
    // Always required
    params.put("textreply", "yes");
    Iterator es = params.entrySet().iterator();
    while (es.hasNext())
    {
      Map.Entry e = (Map.Entry)es.next();
      Object k = e.getKey();
      Object v = e.getValue();

      if (k == null || v == null)
        continue;

      if (msg.length() > 0)
        msg.append("&");

      msg.append(k.toString()).append("=").append(urlEncodeUTF8(v.toString()));
    }
    return msg.toString();
  }

  private static class CheckAccountResponse implements DibsResponse
  {
    private final boolean m_valid;
    private final String m_reason;
    private final String m_actionCode;
    private final Long m_transactionId;

    private CheckAccountResponse(boolean success, Map resultMap)
    {
      m_valid = success;

      m_reason = (String)resultMap.get("reason");
      m_actionCode = (String)resultMap.get("actioncode");
      String transact = (String)resultMap.get("transact");
      m_transactionId = transact == null ? null : Long.valueOf(transact);
    }

    public boolean isValid()
    {
      return m_valid;
    }

    public String getReason()
    {
      return m_reason;
    }

    public String getActionCode()
    {
        return m_actionCode;
    }

    public Long getTransactionId() {
      return m_transactionId;
    }
  }

  private static String urlEncodeUTF8(String s)
  {
    try
    {
      return URLEncoder.encode(s, "UTF-8");
    }
    catch (UnsupportedEncodingException ex)
    {
      // Can't happen
      throw new RuntimeException(ex);
    }
  }

  private static String urlDecodeUTF8(String nn)
  {
    try
    {
      return URLDecoder.decode(nn, "UTF-8");
    }
    catch (UnsupportedEncodingException ex)
    {
      // Can't happen
      throw new RuntimeException(ex);
    }
  }

  private boolean isTesting() {
      return cfg.isTesting();
  }
  
  public String md5of(String src) {
    return MD5(cfg.getMd5K1(), cfg.getMd5K2(), src);
  }
  
  public static String MD5(String src) {
    MessageDigest md;
    try {
      md = MessageDigest.getInstance("MD5");
    } catch (NoSuchAlgorithmException ex) {
      throw new RuntimeException(ex);
    }
    md.update(src.getBytes());
    BigInteger hash = new BigInteger(1, md.digest());
    String res = "0000000000000000000000000000000" + hash.toString(16);
    res = res.substring(res.length() - 32);
    return res;
  }
  
  public static String MD5(String k1, String k2, String src) {
    return MD5(k2 + MD5(k1 + src));
  }

  public interface TransactionInfo
  {
    Long transactionId();
    BigDecimal feeAmount();
    // Return true exactly when the response from DIBS has suspect=true
    // False if response does not contain suspect param or if suspect=false
    Boolean suspect();
    // Return the severity if severity=# is present in response from DIBS,
    // otherwise return null
    Integer severity();
  }

}
