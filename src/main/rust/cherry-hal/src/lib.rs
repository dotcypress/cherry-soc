#![no_std]
#![allow(non_camel_case_types)]

pub extern crate embedded_hal as hal;

pub use cherry_pac::arch::interrupt;
pub use cherry_pac::*;

pub mod gpio;
pub mod prelude;
pub mod time;
