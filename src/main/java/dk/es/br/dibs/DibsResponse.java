
package dk.es.br.dibs;

/**
 *
 * @author osa
 */
public interface DibsResponse<T>
{
    Long transactionId();
    boolean success();
    String reason();
    String actionCode();
    T result();
}
