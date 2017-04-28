
package dk.es.br.dibs;

/**
 *
 * @author osa
 */
public interface DibsResponse {
    /**
     * @return <code>true</code> of the check validated the card
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
}
