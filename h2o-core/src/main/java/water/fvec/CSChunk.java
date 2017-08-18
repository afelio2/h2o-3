package water.fvec;

import water.util.PrettyPrint;
import water.util.UnsafeUtils;

/**
 * Created by tomas on 8/14/17.
 */
public abstract class CSChunk extends Chunk {
  static protected final int _OFF=8+4+4;
  private transient double _scale;
  private transient long _bias;


  CSChunk( byte[] bs, long bias, int scale, int szLog) {
    _mem = bs;
    _start = -1;
    set_len((_mem.length - _OFF) >> szLog);
    _bias = bias;
    UnsafeUtils.set8(_mem, 0, bias);
    UnsafeUtils.set4(_mem, 8, scale);
    _scale = PrettyPrint.pow(1,scale);
  }
  public final double scale() { return _scale; }
  @Override public final byte precision() { return (byte)Math.max(-Math.log10(scale()),0); }

  protected final double getD(int x, int NA){return getD(x,NA,Double.NaN);}

  protected final double getD(int x, int NA, double naImpute){
    if(x == NA) return naImpute;
    return x == NA?naImpute:(_bias + x)*_scale;
  }

  @Override public final boolean hasFloat(){ return _scale < 1; }
  @Override public final void initFromBytes () {
    _start = -1;  _cidx = -1;
    set_len(_mem.length-_OFF);
    _bias = UnsafeUtils.get8 (_mem,0);
    int x = UnsafeUtils.get4(_mem,8);;
    _scale = PrettyPrint.pow(1,x);
  }

  @Override protected final long at8_impl( int i ) {
    double res = atd_impl(i); // note: |mantissa| <= 4B => double is ok
    if(Double.isNaN(res)) throw new IllegalArgumentException("at8_abs but value is missing");
    return (long)res;
  }


  @Override public final boolean set_impl(int idx, long l) {
    double d = (double)l;
    if(d != l) return false;
    return set_impl(idx,d);
  }

  @Override public final boolean set_impl(int idx, float f) {
    return set_impl(idx,(double)f);
  }

  protected final int getScaledValue(double d, int NA){
    assert !Double.isNaN(d):"NaN should be handled separately";
    int x = (int)(((d/_scale))-_bias);
    double d2 = (x+_bias)*_scale;
    if( d!=d2 ) return NA;
    return x;
  }

  protected final int getScaledValue(float f, int NA){
    double d = (double)f;
    assert !Double.isNaN(d):"NaN should be handled separately";
    int x = (int)(((d/_scale))-_bias);
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
  protected abstract <T extends ChunkVisitor> T processRows2(T v, int from, int to, long bias, int exp) ;
  protected abstract <T extends ChunkVisitor> T processRows2(T v, int from, int to);
  protected abstract <T extends ChunkVisitor> T processRows2(T v, int [] ids, long bias, int exp) ;
  protected abstract <T extends ChunkVisitor> T processRows2(T v, int [] ids);
}
