package dk.es.br.dibs;

import java.io.IOException;

/**
 * An exception class used for errors during payment transactions.
 * Contains extra information such as the "errorKey" property
 * returned from DIBS
 *
 * @author     Bruun Rasmussen Kunstauktioner
 * @since      22. maj 2007
 */
public class DibsException
     extends Exception
{
  private String m_key;
  private String m_actionCode;

  DibsException(String message)
  {
    super(message);
  }

  DibsException(IOException cause)
  {
    super("Communication failure", cause);
    m_key = cause.getClass().getName();
  }

  DibsException(String message, String errorKey, String actionCode)
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

  @Override
  public String getMessage()
  {
    return super.getMessage() + ", errorKey=" + m_key + ", actionCode=" + m_actionCode;
  }
}
