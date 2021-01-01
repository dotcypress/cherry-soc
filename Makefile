MODULE = Bootstrap
BUILD_DIR = target/bitstream
CONSTRAINTS = src/main/resources/ice-breaker.pcf

all: bitstream

elaborate: 
	sbt --supershell=never "runMain cherry.$(MODULE)"

bitstream: elaborate
	cd $(BUILD_DIR) && \
	yosys -q -p 'synth_ice40 -top $(MODULE) -json $(MODULE).json' $(MODULE).v && \
	nextpnr-ice40 --up5k --package sg48 --json $(MODULE).json --pcf ../../$(CONSTRAINTS) --asc $(MODULE).asc && \
	icetime -d up5k -mtr $(MODULE).rpt $(MODULE).asc && \
	icepack $(MODULE).asc $(MODULE).bin

cherry-app:
	cd src/main/rust/cherry-app && \
	cargo build --release && \
	riscv32-unknown-elf-objcopy -O ihex -S target/riscv32i-unknown-none-elf/release/cherry-app ../../resources/cherry-app.hex

prog:
	iceprog -S $(BUILD_DIR)/$(MODULE).bin

flash:
	iceprog -p $(BUILD_DIR)/$(MODULE).bin

clean:
	sbt clean --supershell=never
	rm -rf $(BUILD_DIR)
	cd src/main/rust/cherry-app && cargo clean
	cd src/main/rust/cherry-pac && cargo clean

.SECONDARY:
.PHONY: all bitstream build clean elaborate flash prog cherry-app
