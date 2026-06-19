/* @file JedeyeComm.java
 *
 * @author sebastien kister
 * @date 2026
 *
 * @brief TopoDroid JedEye BLE communication
 * --------------------------------------------------------
 *  Copyright This software is distributed under GPL-3.0 or later
 *  See the file COPYING.
 * --------------------------------------------------------
 *
 * JedEye is wire-compatible with the SAP6 shot frame, so the BLE plumbing
 * is inherited from SapComm. Only the GATT UUIDs differ.
 */
package com.topodroid.dev.jedeye;

import com.topodroid.TDX.TopoDroidApp;
import com.topodroid.TDX.TDInstance;
import com.topodroid.TDX.ListerHandler;
import com.topodroid.TDX.Lister;
import com.topodroid.dev.Device;
import com.topodroid.dev.DataType;
import com.topodroid.dev.sap.SapComm;

import android.bluetooth.BluetoothDevice;
import android.os.Bundle;
import android.os.Message;

public class JedeyeComm extends SapComm
{
  private int mStationCounter = 0; // prototype: auto-number stations 0,1,2,...

  /** cstr
   * @param app        application
   * @param address    JedEye address
   * @param bt_device  BT (JedEye) device
   */
  public JedeyeComm( TopoDroidApp app, String address, BluetoothDevice bt_device )
  {
    super( app, address, bt_device, 6 ); // bootstrap as SAP6, then swap UUIDs
    mServiceUuid   = JedeyeConst.JEDEYE_SERVICE_UUID;
    mChrtReadUuid  = JedeyeConst.JEDEYE_CHRT_READ_UUID;
    mChrtWriteUuid = JedeyeConst.JEDEYE_CHRT_WRITE_UUID;
  }

  /** create the protocol
   * @param device   BT device
   */
  @Override
  protected void createProtocol( Device device )
  {
    mSapProto = new JedeyeProtocol( this, device, mContext );
  }

  /** SapComm.assertDevice is widened to protected so we can recognise JedEye
   * here without polluting the SAP package with JedEye knowledge.
   * @param device  bluetooth device
   * @return true if the device is JedEye (correct UUIDs are in place)
   */
  @Override
  protected boolean assertDevice( Device device )
  {
    if ( device.isJedeye() ) {
      assert( mServiceUuid   == JedeyeConst.JEDEYE_SERVICE_UUID );
      assert( mChrtReadUuid  == JedeyeConst.JEDEYE_CHRT_READ_UUID );
      assert( mChrtWriteUuid == JedeyeConst.JEDEYE_CHRT_WRITE_UUID );
      return true;
    }
    return super.assertDevice( device );
  }

  /** Promote a tagged survey LEG to a TopoDroid centerline leg.
   *
   * The inherited handler inserts the raw shot (and sets mLastShotId); we then
   * give it FROM->TO stations, which makes the DBlock a MAIN_LEG. Splays and
   * section names come in later phases. Station numbering here is a placeholder
   * -- the full feature should reconcile it with the survey's station policy or
   * drive it from the device section name. See doc/JedEye_BLE_Protocol.md.
   * @param res        packet type
   * @param lister     data lister
   * @param data_type  packet datatype
   */
  @Override
  public void handleRegularPacket( int res, ListerHandler lister, int data_type )
  {
    super.handleRegularPacket( res, lister, data_type ); // inserts raw shot, sets mLastShotId
    if ( res == DataType.PACKET_DATA
         && mSapProto instanceof JedeyeProtocol
         && ((JedeyeProtocol) mSapProto).shotType() == JedeyeProtocol.SHOT_LEG
         && mLastShotId > 0 ) {
      String from = Integer.toString( mStationCounter );
      String to   = Integer.toString( mStationCounter + 1 );
      TopoDroidApp.mData.updateShotName( mLastShotId, TDInstance.sid, from, to ); // -> MAIN_LEG
      mStationCounter += 1;
      if ( lister != null ) { // re-notify so the UI re-reads the now-named leg
        Message msg = lister.obtainMessage( Lister.LIST_UPDATE );
        Bundle b = new Bundle();
        b.putLong( Lister.BLOCK_ID, mLastShotId );
        msg.setData( b );
        lister.sendMessage( msg );
      }
    }
  }
}
