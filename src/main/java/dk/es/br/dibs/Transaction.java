package dk.es.br.dibs;

import java.math.BigDecimal;

public interface Transaction
{
  Long transactionId();
  String orderId();

  BigDecimal amount();
  BigDecimal feeAmount();
  BigDecimal totalAmount();

  Boolean suspect();
  Integer suspectSeverity();

  String cardBrand();
  String cardType();
  String cardGroup();
  String cardRegion();

}
