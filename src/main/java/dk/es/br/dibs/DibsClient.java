
package dk.es.br.dibs;

import java.io.*;
import java.net.*;
import java.security.Security;

import com.sun.net.ssl.internal.ssl.Provider;
import java.math.BigDecimal;
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
 * check * Use the "ordertext" parameter
 *
 * @author Bruun Rasmussen Kunstauktioner
 * @since 9. september 2004
 * @version $Id: Dibs.java 18573 2013-12-19 13:08:33Z o.sandum $
 */
public class DibsClient
{
  private final static Logger LOG = LoggerFactory.getLogger(DibsClient.class);

  // Most common currency Codes, supported by dibs.
  // We are only using DDK - otherwise consider using LAND db table

  private final static String CURRENCY_DKK = "208";
//public final static String CURRENCY_EUR = "978";
//public final static String CURRENCY_USD = "840";
//public final static String CURRENCY_GBP = "826";
//public final static String CURRENCY_SEK = "752";
//public final static String CURRENCY_AUD = "036";
//public final static String CURRENCY_CAD = "124";
//public final static String CURRENCY_ISK = "352";
//public final static String CURRENCY_JPY = "392";
//public final static String CURRENCY_NZD = "554";
//public final static String CURRENCY_NOK = "578";
//public final static String CURRENCY_CHF = "756";
//public final static String CURRENCY_TRL = "792";  // Server names used to access DIBS/Architrade

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
   * @param account the account to delete
   */
  public void deleteAccount(String accountId)
    throws DibsException
  {
    // First fill out the message to dibs
    Map msg = new HashMap();
    msg.put("merchant", getMerchantId());
    msg.put("ticket", accountId);

    // Query the DIBS server
    try
    {
      Map result = post("/cgi-adm/delticket.cgi", msg, true);
      String status = (String) result.get("status");
      if (status == null || !status.equalsIgnoreCase("ACCEPTED"))
        throw new DibsException("'" + accountId + "': failed to delete account: " + result.get("message") + " (" + result.get("reason") + ")");
    }
    catch (DibsException ex)
    {
      LOG.error("Failed to delete account: " + ex.getMessage());
    }
  }

  /**
   * Checks the validity of the specified account in the DIBS system. This is
   * done by attempting to authorize a 1kr transaction.
   *
   * @param account the account to check
   * @return "ok" if there are no problems. Or the response
   */
  public DibsResponse validateCardSubscription(String accountId)
  {
    // First fill out the message to dibs - authorize a 1kr transfer
    Map params = new HashMap();

    params.put("merchant", getMerchantId());
    params.put("ticket", accountId);
    params.put("orderid", "checking-account");
    params.put("amount", "100");
    params.put("currency", CURRENCY_DKK);

    // Query the DIBS server
    Map result = post("/cgi-ssl/ticket_auth.cgi", params, false);

    String status = (String)result.get("status");

    if ("ACCEPTED".equalsIgnoreCase(status))
    {
      // Checked out find. Now cancel the authorization:
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
        LOG.error("Validate Account: Exception trying to cancel payment " + transact, ex);
      }

      LOG.info(accountId + " checked positive");

      return new CheckAccountResponse(true, result);
    }

    String reason = (String)result.get("reason");
    String message = (String)result.get("message");

    // Presume auth failed. Need to see if the cardholder is to blame
    switch (new Integer(reason).intValue())
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
    catch (IOException ex) {
      LOG.error(url + "["+params+"] failed", ex);
      throw new RuntimeException(url + ": DIBS communication failure", ex);
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
   * @param account the account to check
   * @param orderId the unique order id
   * @param amount the amount of money to deduct
   * @return the transaction id
   */
  public Long withdraw(String accountId, String orderId, BigDecimal amount)
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
    Long transactionId = withdrawCents(accountId, orderId, cents);
    long t2 = System.currentTimeMillis();
    LOG.info("Withdrew " + amount + " from card account " + accountId + ", orderId " + orderId + ": transaction " + transactionId + " (" + (t2-t1) + "ms)");

    return transactionId;
  }

  private Long withdrawCents(String accountId, String orderId, long cents)
    throws DibsException
  {
    // First fill out the message to dibs
    Map msg = new HashMap();
    msg.put("merchant", getMerchantId());
    msg.put("ticket", accountId);
    msg.put("orderid", orderId);
    msg.put("amount", cents);
    msg.put("currency", CURRENCY_DKK);
    msg.put("capturenow", "yes");
    msg.put("uniqueoid", "yes");

    if (isTesting())
      msg.put("test", "yes");

    // Query the DIBS server
    Map result = post("/cgi-ssl/ticket_auth.cgi", msg, false);

    String status = (String)result.get("status");
    String message = (String)result.get("message");
    if (!"ACCEPTED".equals(status))
      throw new DibsException("Withdrawal " + status + ": " + message, errorKey((String)result.get("reason")), (String)result.get("actioncode"));

    String transact = (String)result.get("transact");
    if (StringUtils.isEmpty(transact))
      throw new DibsException("Withdrawal " + status + " without transaction: " + message, errorKey((String)result.get("reason")), (String)result.get("actioncode"));

    return Long.valueOf(transact);
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
      LOG.info(transactionId + ": card type \"" + arg1 + "\"");
      return null;
    }

    // The reply is the card type
    return arg1;
  }

  /**
   * Computes a unique error key based on the "reason" value and the DIBS
   * action. Sadly, DIBS uses the same "reason" values for different error
   * conditions depending on the action performed. If undetermined, null is
   * returned.
   *
   * @param resultMap {@link Map} as generated by the {@link Dibs#parseResponse(String)} function
   */
  static private String errorKey(String reason)
  {
    if (reason == null)
      return null;

    // Get the error as an integer
    int error;
    try
    {
      error = Integer.parseInt(reason);
    }
    catch (Exception e)
    {
      // Don't even try to map the "reson" to an error key
      return null;
    }

    // Note the error codes can be looked up at:
    // http://tech.dibspayment.com/toolbox/dibs_error_codes/
    // "actioncode" is unsupported but matches those shown in the DIBS interface
    return "dibs.auth.error." + error;
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
   * @param urlSpec the server
   * @param message the parameters
   * @param auth should we use basic authentication
   * @return the result
   */
  private String _post(URL url, String message, boolean auth)
    throws IOException
  {
    URLConnection conn = url.openConnection();
    conn.setDoOutput(true);
    conn.setUseCaches(false);

    // DO:
    // conn.setConnectTimeout(...);
    // conn.setReadTimeout(....);

    if (auth)
      conn.setRequestProperty("Authorization", basicAuth());

    PrintWriter wrt = new PrintWriter(conn.getOutputStream());
    try {
      wrt.println(message);
    }
    finally {
      wrt.close();
    }

    StringBuilder res = new StringBuilder();
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
    private final String m_errorKey;
    private final String m_actionCode;
    private final Long m_transactionId;

    private CheckAccountResponse(boolean success, Map resultMap)
    {
      m_valid = success;

      m_errorKey = errorKey((String)resultMap.get("reason"));
      m_actionCode = (String)resultMap.get("actioncode");
      String transact = (String)resultMap.get("transact");
      m_transactionId = transact == null ? null : Long.valueOf(transact);
    }

    public boolean isValid()
    {
      return m_valid;
    }

    public String getErrorKey()
    {
      return m_errorKey;
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
}
