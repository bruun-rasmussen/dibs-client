
package dk.es.br.dibs;

/**
 *
 * @author osa
 */
public interface DibsResponse {
    /**
     * @return <code>true</code> of the check validated the card
     */
    public boolean isValid();
    /**
     * @return {@link String} representation of the error code (if any)
     */
    public String getReason();
    /**
     * @return {@link String} representation of the error action code (if any)
     */
    public String getActionCode();
    /**
     * @return {@link String} representation of the transaction ID (if any)
     */
    public Long getTransactionId();
}
