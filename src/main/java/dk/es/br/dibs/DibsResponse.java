
package dk.es.br.dibs;

/**
 *
 * @author osa
 */
public interface DibsResponse {
    /**
     * @return <code>true</code> if the check validated the card
     */
    boolean isValid();
    /**
     * @return {@link String} representation of the error code (if any)
     */
    String getReason();
    /**
     * @return {@link String} representation of the error action code (if any)
     */
    String getActionCode();
    /**
     * @return {@link String} representation of the transaction ID (if any)
     */
    Long getTransactionId();

  /**
   * @return true exactly when response from DIBS includes suspect=true
   */
  boolean isSuspect();

  /**
   * @return suspect severity according to DIBS if present in response
   * otherwise null
   */
  Long getSeverity();
}
