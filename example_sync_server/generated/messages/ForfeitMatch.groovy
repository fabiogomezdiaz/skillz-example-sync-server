// automatically generated by the FlatBuffers compiler, do not modify

package messages;
import com.skillz.server.Message;
import java.nio.*;
import java.lang.*;
import java.util.*;
import com.google.flatbuffers.*;

@SuppressWarnings("unused")
public final class ForfeitMatch extends Message {
  public static void ValidateVersion() { Constants.FLATBUFFERS_1_12_0(); }
  public ForfeitMatch get(ByteBuffer _bb) { return get(_bb, new ForfeitMatch()); }
  public ForfeitMatch get(ByteBuffer _bb, ForfeitMatch obj) { _bb.order(ByteOrder.LITTLE_ENDIAN); return (obj.__assign(_bb.getInt(_bb.position()) + _bb.position(), _bb)); }
  public void __init(int _i, ByteBuffer _bb) { __reset(_i, _bb); }
  public ForfeitMatch __assign(int _i, ByteBuffer _bb) { __init(_i, _bb); return this; }

  public short opcode() { int o = __offset(4); return o != 0 ? bb.getShort(o + bb_pos) : 3; }

  public static int createForfeitMatch(FlatBufferBuilder builder,
      short opcode) {
    builder.startTable(1);
    ForfeitMatch.addOpcode(builder, opcode);
    return ForfeitMatch.endForfeitMatch(builder);
  }

  public static void startForfeitMatch(FlatBufferBuilder builder) { builder.startTable(1); }
  public static void addOpcode(FlatBufferBuilder builder, short opcode) { builder.addShort(0, opcode, 3); }
  public static int endForfeitMatch(FlatBufferBuilder builder) {
    int o = builder.endTable();
    return o;
  }

  public static final class Vector extends BaseVector {
    public Vector __assign(int _vector, int _element_size, ByteBuffer _bb) { __reset(_vector, _element_size, _bb); return this; }

    public ForfeitMatch get(int j) { return get(new ForfeitMatch(), j); }
    public ForfeitMatch get(ForfeitMatch obj, int j) {  return obj.__assign(__indirect(__element(j), bb), bb); }
  }
}
