package cherry.soc

import spinal.core._
import spinal.lib._
import spinal.lib.bus.misc._
import spinal.lib.bus.simple._
import vexriscv.plugin._

class MainBusArbiter(memoryBusConfig: PipelinedMemoryBusConfig)
    extends Component {
  val io = new Bundle {
    val iBus = slave(IBusSimpleBus(null))
    val dBus = slave(DBusSimpleBus(false))
    val masterBus = master(PipelinedMemoryBus(memoryBusConfig))
  }

  io.masterBus.cmd.valid := io.iBus.cmd.valid || io.dBus.cmd.valid
  io.masterBus.cmd.write := io.dBus.cmd.valid && io.dBus.cmd.wr
  io.masterBus.cmd.address := io.dBus.cmd.valid ? io.dBus.cmd.address | io.iBus.cmd.pc
  io.masterBus.cmd.data := io.dBus.cmd.data
  io.masterBus.cmd.mask := io.dBus.genMask(io.dBus.cmd)
  io.iBus.cmd.ready := io.masterBus.cmd.ready && !io.dBus.cmd.valid
  io.dBus.cmd.ready := io.masterBus.cmd.ready

  val rspPending = RegInit(False) clearWhen (io.masterBus.rsp.valid)
  val rspTarget = RegInit(False)

  when(io.masterBus.cmd.fire && !io.masterBus.cmd.write) {
    rspTarget := io.dBus.cmd.valid
    rspPending := True
  }

  when(rspPending && !io.masterBus.rsp.valid) {
    io.iBus.cmd.ready := False
    io.dBus.cmd.ready := False
    io.masterBus.cmd.valid := False
  }

  io.iBus.rsp.valid := io.masterBus.rsp.valid && !rspTarget
  io.iBus.rsp.inst := io.masterBus.rsp.data
  io.iBus.rsp.error := False

  io.dBus.rsp.ready := io.masterBus.rsp.valid && rspTarget
  io.dBus.rsp.data := io.masterBus.rsp.data
  io.dBus.rsp.error := False
}
