#![no_std]
#![no_main]

extern crate panic_halt;

use cherry_hal::{gpioa::GPIOA, prelude::*, rt::entry};

#[entry]
fn main() -> ! {
  let gpioa = GPIOA::take().unwrap().split();

  let mut led_0 = gpioa.pa0.into_output();
  let mut led_1 = gpioa.pa1.into_output();
  let mut led_2 = gpioa.pa2.into_output();
  let mut led_3 = gpioa.pa3.into_output();
  let mut led_4 = gpioa.pa4.into_output();
  let btn_1 = gpioa.pa31;
  let btn_2 = gpioa.pa30;
  let btn_3 = gpioa.pa29;

  let mut cnt = 0;
  loop {
    cnt += 1;

    let hit = (cnt >> 16) & 1 == 0;
    led_1.set_level(hit).ok();
    led_2.set_level(!hit).ok();

    let shift = if btn_1.is_high().unwrap() { 15 } else { 14 };
    let hit = (cnt >> shift) & 1 == 0;
    led_3.set_level(hit).ok();
    led_4.set_level(!hit).ok();

    if btn_2.is_high().unwrap() || btn_3.is_high().unwrap() {
      let hit = (cnt >> 13) & 1 == 0;
      led_0.set_level(hit).ok();
    }
  }
}
