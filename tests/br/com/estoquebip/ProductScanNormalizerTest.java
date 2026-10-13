package br.com.estoquebip;
import java.util.Arrays;
public final class ProductScanNormalizerTest {
 private static void check(String input,String expected) {
  String actual=ProductScanNormalizer.normalize(input);
  if (!expected.equals(actual)) throw new AssertionError(input+" expected "+expected+" got "+actual);
 }
 public static void main(String[] args) {
  check("00010187403","10187403");
  check("00010181218814","10181218");
  check("001018740399","10187403");
  check("101874039999","10187403");
  check("21234567890123","21234567890");
  check("31234567890123","31234567890");
  check("41234567890123","41234567890");
  check("V-ABC-123","V-ABC-123");
  check("N-987654","N-987654");
  check(" 000 10187403 ","10187403");
  check("ABC-123","ABC-123");
  check("","");
  String[] expected={"26020100171","36020100171","46020100171"};
  if (!ProductScanNormalizer.candidates("602010017184").equals(Arrays.asList(expected))) {
   throw new AssertionError("Candidate resolution mismatch");
  }
  System.out.println("OK: 13 testes de normalização");
 }
}
