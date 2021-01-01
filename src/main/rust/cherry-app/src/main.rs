#![no_std]
#![no_main]

extern crate panic_halt;

use cherry_pac::gpio::GPIO;
use riscv_rt::entry;

#[entry]
fn main() -> ! {
  let gpio = GPIO::take().unwrap();
  gpio.DIRECTION.write(0xff);

  let mut cnt = 0;
  loop {
    cnt += 1;
    gpio.OUTPUT.write(cnt >> 16);
  }
}
