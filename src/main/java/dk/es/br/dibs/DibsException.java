package dk.es.br.dibs;

/**
 * An exception class used for errors during payment transactions.
 * Contains extra information such as the "errorKey" property
 * returned from DIBS
 *
 * @author     Bruun Rasmussen Kunstauktioner
 * @since      22. maj 2007
 * @version    $Id: DibsException.java 11156 2008-08-22 10:07:17Z peder $
 */
public class DibsException
     extends Exception
{
  private String m_key;
  private String m_actionCode;

  public DibsException(String message)
  {
    super(message);
  }

  protected DibsException(String message, Throwable cause)
  {
    super(message, cause);
  }

  protected DibsException(String message, String errorKey)
  {
    this(message);
    m_key = errorKey;
  }

  protected DibsException(String message, String errorKey, String actionCode)
  {
    this(message);
    m_key = errorKey;
    m_actionCode = actionCode;
  }

  /**
   * If defined, returns the unique error key,
   * which could e.g. be used to define error messages
   * in a resource bundle
   *
   * @return    the unique error key
   */
  public String getErrorKey()
  {
    return m_key;
  }

  /**
   * Sets the unique error key,
   * which could e.g. be used to define error messages
   * in a resource bundle.
   *
   * @param  errorKey  the unique error key
   */
  public void setErrorKey(String errorKey)
  {
    m_key = errorKey;
  }

  /**
   * If defined, returns the Payment Service action code
   *
   * @return    the Payment Service action code
   */
  public String getActionCode()
  {
    return m_actionCode;
  }

  /**
   * Sets the Payment Service action code
   *
   * @param  errorKey  the Payment Service action code
   */
  public void setActionCode(String actionCode)
  {
    m_actionCode = actionCode;
  }

  /**
   * Returns a string representation of the error
   *
   * @return    a string representation of the error
   */
  public String toString()
  {
    return super.toString() + ", errorKey=" + m_key + ", actionCode=" + m_actionCode;
  }
}
