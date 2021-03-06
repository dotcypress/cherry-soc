#![no_std]

pub use riscv as arch;

pub mod register;
pub use crate::register::{RORegister, UnsafeRORegister};
pub use crate::register::{RWRegister, UnsafeRWRegister};
pub use crate::register::{UnsafeWORegister, WORegister};

mod cherry;
pub use cherry::*;

#[cfg(feature = "rt")]
pub use riscv_rt as rt;
