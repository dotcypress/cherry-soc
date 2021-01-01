package cherry.soc

import spinal.core._
import spinal.lib._
import spinal.lib.bus.misc._
import spinal.lib.bus.simple._
import spinal.lib.misc.HexTools
import vexriscv.plugin._

case class Ram(
    onChipRamSize: BigInt,
    onChipRamHexFile: String,
    memoryBusConfig: PipelinedMemoryBusConfig,
    bigEndian: Boolean = false
) extends Component {
  val io = new Bundle {
    val bus = slave(PipelinedMemoryBus(memoryBusConfig))
  }

  val ram = Mem(Bits(32 bits), onChipRamSize / 4)
  io.bus.rsp.valid := RegNext(io.bus.cmd.fire && !io.bus.cmd.write) init (False)
  io.bus.rsp.data := ram.readWriteSync(
    address = (io.bus.cmd.address >> 2).resized,
    data = io.bus.cmd.data,
    enable = io.bus.cmd.valid,
    write = io.bus.cmd.write,
    mask = io.bus.cmd.mask
  )
  io.bus.cmd.ready := True

  if (onChipRamHexFile != null) {
    HexTools.initRam(ram, onChipRamHexFile, 0x80000000L)
  }
}

case class MemoryBusDecoder(
    master: PipelinedMemoryBus,
    val specification: Seq[(PipelinedMemoryBus, SizeMapping)],
    pipelineMaster: Boolean
) extends Area {
  val masterPipelined = PipelinedMemoryBus(master.config)

  if (!pipelineMaster) {
    masterPipelined.cmd << master.cmd
    masterPipelined.rsp >> master.rsp
  } else {
    masterPipelined.cmd <-< master.cmd
    masterPipelined.rsp >> master.rsp
  }

  val slaveBuses = specification.map(_._1)
  val memorySpaces = specification.map(_._2)

  val hits = for ((slaveBus, memorySpace) <- specification) yield {
    val hit = memorySpace.hit(masterPipelined.cmd.address)
    slaveBus.cmd.valid := masterPipelined.cmd.valid && hit
    slaveBus.cmd.payload := masterPipelined.cmd.payload.resized
    hit
  }

  val noHit = !hits.orR
  masterPipelined.cmd.ready := (hits, slaveBuses).zipped
    .map(_ && _.cmd.ready)
    .orR || noHit

  val rspPending = RegInit(False)
  rspPending.clearWhen(
    masterPipelined.rsp.valid
  ) setWhen (masterPipelined.cmd.fire && !masterPipelined.cmd.write)

  val rspNoHit = RegNext(False) init (False) setWhen (noHit)
  val rspSourceId = RegNextWhen(OHToUInt(hits), masterPipelined.cmd.fire)
  masterPipelined.rsp.valid := slaveBuses
    .map(_.rsp.valid)
    .orR || (rspPending && rspNoHit)
  masterPipelined.rsp.payload := slaveBuses.map(_.rsp.payload).read(rspSourceId)

  when(rspPending && !masterPipelined.rsp.valid) {
    masterPipelined.cmd.ready := False
    slaveBuses.foreach(_.cmd.valid := False)
  }
}
