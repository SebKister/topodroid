/* @file JedeyeProtocol.java
 *
 * @author sebastien kister
 * @date 2026
 *
 * @brief TopoDroid JedEye BLE protocol
 * --------------------------------------------------------
 *  Copyright This software is distributed under GPL-3.0 or later
 *  See the file COPYING.
 * --------------------------------------------------------
 *
 * JedEye's distance-meter tool uses the SAP6 17-byte shot frame (sequence byte +
 * four little-endian float32 = bearing/clino/roll/distance), parsed by the
 * inherited SapProtocol. Survey mode additionally streams tagged frames (>= 18
 * bytes, message type in byte [0]); those are parsed here. See
 * doc/JedEye_BLE_Protocol.md in the firmware repo.
 */
package com.topodroid.dev.jedeye;

import com.topodroid.dev.Device;
import com.topodroid.dev.DataType;
import com.topodroid.dev.sap.SapProtocol;

import android.content.Context;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

class JedeyeProtocol extends SapProtocol
{
  // kind of the most recently parsed shot
  static final int SHOT_LEGACY = 0; // untagged 17-byte distance-meter shot
  static final int SHOT_LEG    = 1; // survey centerline leg
  static final int SHOT_SPLAY  = 2; // survey room/wall splay

  // tagged-frame message types (byte [0]); see firmware src/net/bluetooth.cpp
  private static final int FRAME_SURVEY_LEG   = 0x01;
  private static final int FRAME_SURVEY_SPLAY = 0x02;

  private int mShotType = SHOT_LEGACY;

  /** cstr
   * @param comm    communication class
   * @param device  BT device
   * @param context context
   */
  JedeyeProtocol( JedeyeComm comm, Device device, Context context )
  {
    super( comm, device, context );
  }

  /** @return the kind (SHOT_LEG / SHOT_LEGACY) of the most recently parsed shot */
  int shotType() { return mShotType; }

  /** Parse a JedEye notify frame.
   *
   * Tagged survey frames are >= 18 bytes with the message type in byte [0]:
   *   [0] type, [1] seq, [2..5] bearing, [6..9] clino, [10..13] roll,
   *   [14..17] distance  (little-endian float32; deg / deg / deg / m).
   * The legacy 17-byte distance-meter frame has no tag and falls through to the
   * inherited SAP6 parser.
   *
   * @param bytes received frame
   * @return packet type (DataType.PACKET_DATA on a shot)
   */
  @Override
  public int handleRead( byte[] bytes )
  {
    if ( bytes != null && bytes.length >= 18 ) {
      int type = bytes[0] & 0xff;
      if ( type == FRAME_SURVEY_LEG || type == FRAME_SURVEY_SPLAY ) {
        ByteBuffer bb = ByteBuffer.wrap( bytes ).order( ByteOrder.LITTLE_ENDIAN );
        mBearing  = bb.getFloat( 2 );
        mClino    = bb.getFloat( 6 );
        mRoll     = bb.getFloat( 10 );
        mDistance = bb.getFloat( 14 );
        mShotType = ( type == FRAME_SURVEY_LEG ) ? SHOT_LEG : SHOT_SPLAY;
        return DataType.PACKET_DATA;
      }
    }
    mShotType = SHOT_LEGACY;
    return super.handleRead( bytes );
  }
}
