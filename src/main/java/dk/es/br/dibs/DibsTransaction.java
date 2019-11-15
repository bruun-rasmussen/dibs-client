package dk.es.br.dibs;

import java.math.BigDecimal;

/**
 * Adapter for Transaction interface
 */
public class DibsTransaction implements Transaction
{
  @Override
  public Long transactionId()
  {
    return null;
  }

  @Override
  public String orderId()
  {
    return null;
  }

  @Override
  public BigDecimal amount()
  {
    return null;
  }

  @Override
  public BigDecimal feeAmount()
  {
    return null;
  }

  @Override
  public BigDecimal totalAmount()
  {
    return null;
  }

  @Override
  public Boolean suspect()
  {
    return null;
  }

  @Override
  public Integer suspectSeverity()
  {
    return null;
  }

  @Override
  public String cardBrand()
  {
    return null;
  }

  @Override
  public String cardType()
  {
    return null;
  }

  @Override
  public String cardGroup()
  {
    return null;
  }

  @Override
  public String cardRegion()
  {
    return null;
  }
}
