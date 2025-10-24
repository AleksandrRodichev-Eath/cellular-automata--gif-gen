use std::fmt;
use std::str::FromStr;

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct Rule {
    born: [bool; 9],
    survive: [bool; 9],
}

impl Rule {
    pub fn new(born: [bool; 9], survive: [bool; 9]) -> Self {
        Self { born, survive }
    }

    pub fn default_life() -> Self {
        "B3/S23".parse().expect("default rule is valid")
    }

    pub fn should_live(&self, currently_alive: bool, neighbor_count: u8) -> bool {
        let idx = neighbor_count as usize;
        if currently_alive {
            self.survive[idx]
        } else {
            self.born[idx]
        }
    }
}

impl Default for Rule {
    fn default() -> Self {
        Self::default_life()
    }
}

impl FromStr for Rule {
    type Err = RuleParseError;

    fn from_str(s: &str) -> Result<Self, Self::Err> {
        parse_rule(s)
    }
}

fn parse_rule(raw: &str) -> Result<Rule, RuleParseError> {
    let trimmed = raw.trim();
    if trimmed.is_empty() {
        return Err(RuleParseError::Empty);
    }

    let mut parts = trimmed.split('/');
    let born_part = parts.next().ok_or(RuleParseError::InvalidFormat)?;
    let survive_part = parts.next().ok_or(RuleParseError::InvalidFormat)?;

    if parts.next().is_some() {
        return Err(RuleParseError::InvalidFormat);
    }

    let born_digits = parse_segment(born_part, 'B')?;
    let survive_digits = parse_segment(survive_part, 'S')?;

    let born = digits_to_flags(&born_digits)?;
    let survive = digits_to_flags(&survive_digits)?;

    Ok(Rule::new(born, survive))
}

fn parse_segment(segment: &str, expected_prefix: char) -> Result<Vec<u8>, RuleParseError> {
    let mut chars = segment.chars();
    let prefix = chars.next().ok_or(RuleParseError::InvalidFormat)?;
    if prefix.to_ascii_uppercase() != expected_prefix {
        return Err(RuleParseError::InvalidFormat);
    }

    let mut digits = Vec::new();
    for ch in chars {
        if !ch.is_ascii_digit() {
            return Err(RuleParseError::InvalidDigit(ch));
        }
        let value = ch.to_digit(10).ok_or(RuleParseError::InvalidDigit(ch))? as u8;
        if value > 8 {
            return Err(RuleParseError::OutOfRange(value));
        }
        if digits.contains(&value) {
            return Err(RuleParseError::DuplicatedDigit(value));
        }
        digits.push(value);
    }
    Ok(digits)
}

fn digits_to_flags(digits: &[u8]) -> Result<[bool; 9], RuleParseError> {
    let mut flags = [false; 9];
    for &digit in digits {
        if flags[digit as usize] {
            return Err(RuleParseError::DuplicatedDigit(digit));
        }
        flags[digit as usize] = true;
    }
    Ok(flags)
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum RuleParseError {
    Empty,
    InvalidFormat,
    InvalidDigit(char),
    OutOfRange(u8),
    DuplicatedDigit(u8),
}

impl fmt::Display for RuleParseError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            RuleParseError::Empty => write!(f, "rule string is empty"),
            RuleParseError::InvalidFormat => write!(f, "rule must follow B#/S# format"),
            RuleParseError::InvalidDigit(ch) => write!(f, "invalid digit '{ch}' in rule"),
            RuleParseError::OutOfRange(d) => write!(f, "neighbor count {d} is out of range 0-8"),
            RuleParseError::DuplicatedDigit(d) => write!(f, "digit {d} is duplicated in rule"),
        }
    }
}

impl std::error::Error for RuleParseError {}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parses_standard_rule() {
        let rule = "B3/S23".parse::<Rule>().unwrap();
        assert!(rule.should_live(false, 3));
        assert!(rule.should_live(true, 2));
        assert!(rule.should_live(true, 3));
        assert!(!rule.should_live(false, 2));
    }

    #[test]
    fn rejects_duplicate_digits() {
        let err = "B33/S23".parse::<Rule>().unwrap_err();
        assert!(matches!(err, RuleParseError::DuplicatedDigit(3)));
    }

    #[test]
    fn rejects_invalid_prefix() {
        let err = "C3/S23".parse::<Rule>().unwrap_err();
        assert!(matches!(err, RuleParseError::InvalidFormat));
    }

    #[test]
    fn rejects_digits_out_of_range() {
        let err = "B9/S23".parse::<Rule>().unwrap_err();
        assert!(matches!(err, RuleParseError::OutOfRange(9)));
    }
}
