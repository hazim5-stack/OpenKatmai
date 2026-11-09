package org.openkatmai.flighttrace;
public final class PolicyTest {
 static void check(boolean ok){if(!ok)throw new AssertionError();}
 public static void main(String[] args){
  check(!PositionPolicy.valid(0,0));check(!PositionPolicy.valid(Double.NaN,30));check(!PositionPolicy.valid(91,30));check(!PositionPolicy.valid(20,181));
  check(PositionPolicy.valid(0,30));check(PositionPolicy.valid(-90,-180));
  check(!PositionPolicy.fresh(-1));check(PositionPolicy.fresh(4000));check(!PositionPolicy.fresh(4001));
  check(!PositionPolicy.join(0,1000));check(!PositionPolicy.join(5000,4000));check(PositionPolicy.join(1000,5000));check(!PositionPolicy.join(1000,5001));
  check(PositionPolicy.distance(21,39,21,39)==0);check(Math.abs(PositionPolicy.distance(0,0,0,1)-111195)<5);
  System.out.println("PolicyTest: passed (coordinates, stale data, gaps and distance)");
 }
}
