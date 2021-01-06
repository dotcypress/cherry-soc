#![no_std]
#![no_main]

use core::fmt::Write;

use cherry_hal::gpioa::GPIOA;
use cherry_hal::prelude::*;
use cherry_hal::rt::entry;
use cherry_hal::serial;
use cherry_hal::timer1::TIMER1;
use cherry_hal::uart1::UART1;

#[entry]
fn main() -> ! {
  let gpioa = GPIOA::take().unwrap().split();
  let mut uart = UART1::take().unwrap().serial(serial::Config::default());
  let mut timer = TIMER1::take().unwrap().timer();

  write!(uart, "\r\nBooting Cherry\r\n").ok();

  let mut led_0 = gpioa.pa0.into_output();
  let mut led_1 = gpioa.pa1.into_output();
  let mut led_2 = gpioa.pa2.into_output();
  let mut led_3 = gpioa.pa3.into_output();
  let mut led_4 = gpioa.pa4.into_output();

  let btn_1 = gpioa.pa31;
  let btn_2 = gpioa.pa30;
  let btn_3 = gpioa.pa29;

  uart.rx().listen();
  unsafe {
    cherry_hal::arch::register::mstatus::set_mie();
    cherry_hal::arch::register::mie::set_mext();
  }

  led_1.set_high().ok();
  led_2.set_low().ok();
  led_3.set_high().ok();
  led_4.set_low().ok();

  loop {
    led_1.toggle().ok();
    led_2.toggle().ok();
    led_3.toggle().ok();
    led_4.toggle().ok();

    if btn_1.is_high().unwrap() {
      panic!("Hello panic");
    }

    if btn_2.is_high().unwrap() {
      led_0.toggle().ok();
    }

    if btn_3.is_low().unwrap() {
      timer.delay(100.ms());
    } else {
      timer.delay(500.ms());
    }
  }
}

#[export_name = "MachineExternal"]
fn uart_interrupt() {
  let mut uart = unsafe { UART1::conjure().serial(serial::Config::default()) };
  match uart.read() {
    Ok(byte) => {
      uart.write(byte).ok();
    }
    Err(cherry_hal::nb::Error::WouldBlock) => {}
    Err(_) => {
      panic!("Serial fault");
    }
  }
}
