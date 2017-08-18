package water.fvec;

import water.H2O;
import water.util.PrettyPrint;
import water.util.UnsafeUtils;

/**
 * Created by tomas on 8/18/17.
 */
public final class CDecimalChunk extends Chunk {
  static protected final int _OFF=8+4+4;
  private transient double _scale;
  private transient long _bias;
  private transient int _valSz; // {0,1,2}
  private transient int _NA; // (C1|C2|C4)._NA

  CDecimalChunk( byte[] bs, long bias, int scale, int szLog) {
    assert scale < 0;
    _mem = bs;
    _start = -1;
    set_len((_mem.length - _OFF) >> szLog);
    _bias = bias;
    UnsafeUtils.set8(_mem, 0, bias);
    UnsafeUtils.set4(_mem, 8, scale);
    UnsafeUtils.set4(_mem, 12, szLog);
    _scale = PrettyPrint.pow(1,-scale);
    _valSz = szLog;
    setNA();
  }

  private void setNA(){
    switch(_valSz){
      case 0: _NA = C1Chunk._NA; break;
      case 1: _NA = C2Chunk._NA; break;
      case 2: _NA = C4Chunk._NA; break;
      default: throw H2O.unimpl();
    }
  }

  public final double scale() { return 1.0/_scale;}
  @Override public final byte precision() { return (byte)Math.max(-Math.log10(scale()),0); }

  private int getMantissa(int i){
    switch(_valSz){
      case 0: return 0xFF&_mem[_OFF+i];
      case 1: return UnsafeUtils.get2(_mem,_OFF+2*i);
      case 2: return UnsafeUtils.get4(_mem,_OFF+4*i);
      default: throw H2O.unimpl();
    }
  }

  protected final double getD(int x, double naImpute){
    return x == _NA?naImpute:(_bias + x)/_scale;
  }

  @Override public final boolean hasFloat(){ return true; }
  @Override public final void initFromBytes () {
    _start = -1;  _cidx = -1;
    _bias = UnsafeUtils.get8(_mem,0);
    int x = UnsafeUtils.get4(_mem,8);
    _scale = PrettyPrint.pow(1,-x);
    _valSz = UnsafeUtils.get4(_mem,12);
    set_len((_mem.length-_OFF)>>_valSz);
    setNA();
  }

  @Override protected final long at8_impl( int i ) {
    double res = atd_impl(i); // note: |mantissa| <= 4B => double is ok
    if(Double.isNaN(res)) throw new IllegalArgumentException("at8_abs but value is missing");
    return (long)res;
  }

  @Override
  boolean isNA_impl(int idx) {return getMantissa(idx) == _NA;}

  public final double atd_impl(int i){return getD(i,Double.NaN);}

  @Override public final boolean set_impl(int idx, long l) {
    double d = (double)l;
    if(d != l) return false;
    return set_impl(idx,d);
  }

  @Override
  boolean set_impl(int idx, double d) {
    return false;
  }

  @Override public final boolean set_impl(int idx, float f) {
    return set_impl(idx,(double)f);
  }

  @Override
  boolean setNA_impl(int idx) {
    return false;
  }

  protected final int getScaledValue(double d, int NA){
    assert !Double.isNaN(d):"NaN should be handled separately";
    int x = (int)((d/_scale)-_bias);
    double d2 = (x+_bias)/_scale;
    if( d!=d2 ) return NA;
    return x;
  }

  protected final int getScaledValue(float f, int NA){
    double d = (double)f;
    assert !Double.isNaN(d):"NaN should be handled separately";
    int x = (int)((d/_scale)-_bias);
    float f2 = (float)((x+_bias)*_scale);
    if( f!=f2 ) return NA;
    return x;
  }

  @Override
  public final <T extends ChunkVisitor> T processRows(T v, int from, int to) {
    if(v.expandedVals()){
      processRows2(v,from,to,_bias,UnsafeUtils.get4(_mem,8));
    } else
      processRows2(v,from,to);
    return v;
  }

  @Override
  public <T extends ChunkVisitor> T processRows(T v, int[] ids) {
    if(v.expandedVals()){
      processRows2(v,ids,_bias,UnsafeUtils.get4(_mem,8));
    } else
      processRows2(v,ids);
    return v;
  }
  private <T extends ChunkVisitor> T processRows2(T v, int from, int to, long bias, int exp) {
    for(int i = from; i < to; ++i){
      long m = getMantissa(i);
      if(m == _NA)v.addNAs(1); else v.addValue(m+bias,exp);
    }
    return v;
  }
  private <T extends ChunkVisitor> T processRows2(T v, int from, int to){
    for(int i = from; i < to; ++i)
      v.addValue(getD(getMantissa(i),Double.NaN));
    return v;
  }
  private  <T extends ChunkVisitor> T processRows2(T v, int [] ids, long bias, int exp) {
    for(int i:ids){
      long m = getMantissa(i);
      if(m == _NA)v.addNAs(1); else v.addValue(m+bias,exp);
    }
    return v;
  }
  private  <T extends ChunkVisitor> T processRows2(T v, int [] ids){
    for(int i:ids)
      v.addValue(getD(getMantissa(i),Double.NaN));
    return v;
  }

  /**
   * Dense bulk interface, fetch values from the given range
   * @param vals
   * @param from
   * @param to
   */
  @Override
  public double [] getDoubles(double [] vals, int from, int to, double NA){
    for(int i = from; i < to; ++i)
      vals[i-from] = getD(getMantissa(i),NA);
    return vals;
  }
  /**
   * Dense bulk interface, fetch values from the given ids
   * @param vals
   * @param ids
   */
  @Override
  public double [] getDoubles(double [] vals, int [] ids){
    int j = 0;
    for(int i:ids)
      vals[j++] = getD(getMantissa(i),Double.NaN);
    return vals;
  }

}
