#include "encoding.h"
#include <string.h>

constexpr Encoding::Letter Encoding::m_letters[];

// Convert CP874 / CP1251 single-byte character (or existing UTF-8) to UTF-8
std::string Encoding::cp2utf(const std::string& s)
{
	std::string ns;
	ns.reserve(s.size() * 3);

	const unsigned char* p = reinterpret_cast<const unsigned char*>(s.c_str());
	size_t len = s.size();
	size_t i = 0;

	while (i < len)
	{
		unsigned char c = p[i];

		// 1. Single-byte ASCII (0x00..0x7F)
		if (c < 0x80)
		{
			ns.push_back((char)c);
			i++;
			continue;
		}

		// 2. Check if already a valid 3-byte UTF-8 Thai character (0xE0, 0xB8/0xB9, 0x80..0xBF)
		if (c == 0xE0 && (i + 2 < len))
		{
			unsigned char b2 = p[i + 1];
			unsigned char b3 = p[i + 2];
			if ((b2 == 0xB8 || b2 == 0xB9) && (b3 >= 0x80 && b3 <= 0xBF))
			{
				ns.push_back((char)c);
				ns.push_back((char)b2);
				ns.push_back((char)b3);
				i += 3;
				continue;
			}
		}

		// 3. Check for valid 4-byte UTF-8 (e.g. emojis U+10000..U+10FFFF)
		if ((c & 0xF8) == 0xF0 && (i + 3 < len) &&
			((p[i + 1] & 0xC0) == 0x80) &&
			((p[i + 2] & 0xC0) == 0x80) &&
			((p[i + 3] & 0xC0) == 0x80))
		{
			ns.push_back((char)c);
			ns.push_back((char)p[i + 1]);
			ns.push_back((char)p[i + 2]);
			ns.push_back((char)p[i + 3]);
			i += 4;
			continue;
		}

		// 4. CP874 / TIS-620 Thai character range (0xA1..0xFB):
		// Consonants, vowels, baht sign (0xA1..0xDF) -> Unicode U+0E01..U+0E3F
		if (c >= 0xA1 && c <= 0xDF)
		{
			ns.push_back((char)0xE0);
			ns.push_back((char)0xB8);
			ns.push_back((char)(c - 0x20));
			i++;
			continue;
		}
		// Leading vowels, tone marks, digits (0xE0..0xFB) -> Unicode U+0E40..U+0E5B
		else if (c >= 0xE0 && c <= 0xFB)
		{
			ns.push_back((char)0xE0);
			ns.push_back((char)0xB9);
			ns.push_back((char)(c - 0x60));
			i++;
			continue;
		}

		// 5. Check if it's an existing valid 2-byte UTF-8 sequence (e.g. Cyrillic U+0400..U+04FF)
		// Only check for bytes not in the Thai single-byte range handled above
		if ((c & 0xE0) == 0xC0 && (i + 1 < len) && ((p[i + 1] & 0xC0) == 0x80))
		{
			ns.push_back((char)c);
			ns.push_back((char)p[i + 1]);
			i += 2;
			continue;
		}

		// 6. Any other 3-byte UTF-8 sequence
		if ((c & 0xF0) == 0xE0 && (i + 2 < len) && ((p[i + 1] & 0xC0) == 0x80) && ((p[i + 2] & 0xC0) == 0x80))
		{
			ns.push_back((char)c);
			ns.push_back((char)p[i + 1]);
			ns.push_back((char)p[i + 2]);
			i += 3;
			continue;
		}

		// 7. Fallback for other high-ASCII bytes (e.g. 0x80..0xA0, 0xFC..0xFF)
		char buf[8] = { 0 };
		char in[2] = { (char)c, 0 };
		convert_windows1251_to_utf8(buf, in, 1);
		ns.append(buf);
		i++;
	}

	return ns;
}

std::string Encoding::utf2cp(const std::string& s)
{
	size_t len = s.size();
	const char* buff = s.c_str();
	char* output = new char[len + 1];
	convert_utf8_to_windows1251(buff, output, len);
	std::string ns(output);
	delete[] output;
	return ns;
}

bool Encoding::convert_utf8_to_windows1251(const char* utf8, char* windows1251, size_t n)
{
	int i = 0;
	int j = 0;
	const unsigned char* u = reinterpret_cast<const unsigned char*>(utf8);

	for (; i < (int)n && u[i] != 0; ++i) {
		unsigned char prefix = u[i];

		if ((prefix & 0x80) == 0) {
			windows1251[j++] = (char)prefix;
		}
		// 3-byte UTF-8: Check for Thai range U+0E00..U+0E7F (0xE0, 0xB8/B9, 0x80..0xBF)
		else if (prefix == 0xE0 && i + 2 < (int)n && (u[i + 1] == 0xB8 || u[i + 1] == 0xB9)) {
			unsigned char b2 = u[i + 1];
			unsigned char b3 = u[i + 2];
			if (b2 == 0xB8 && b3 >= 0x81 && b3 <= 0xBF) {
				windows1251[j++] = (char)(b3 + 0x20); // 0xA1..0xDF in CP874
				i += 2;
			}
			else if (b2 == 0xB9 && b3 >= 0x80 && b3 <= 0xBB) {
				windows1251[j++] = (char)(b3 + 0x60); // 0xE0..0xFB in CP874
				i += 2;
			}
			else {
				// Non-convertible Thai char, keep or skip
				i += 2;
			}
		}
		// 2-byte UTF-8: Cyrillic / Latin
		else if ((~prefix) & 0x20) {
			unsigned char suffix = u[i + 1];
			int first5bit = prefix & 0x1F;
			first5bit <<= 6;
			int sec6bit = suffix & 0x3F;
			int unicode_char = first5bit + sec6bit;

			if (unicode_char >= 0x410 && unicode_char <= 0x44F) {
				windows1251[j++] = (char)(unicode_char - 0x350);
			}
			else if (unicode_char >= 0x80 && unicode_char <= 0xFF) {
				windows1251[j++] = (char)(unicode_char);
			}
			else if (unicode_char >= 0x402 && unicode_char <= 0x403) {
				windows1251[j++] = (char)(unicode_char - 0x382);
			}
			else {
				int count = sizeof(m_letters) / sizeof(Letter);
				bool matched = false;
				for (int k = 0; k < count; ++k) {
					if (unicode_char == m_letters[k].unicode) {
						windows1251[j++] = m_letters[k].win1251;
						matched = true;
						break;
					}
				}
				if (!matched) {
					windows1251[j++] = '?';
				}
			}
			++i;
		}
		else {
			windows1251[j++] = '?';
		}
	}
	windows1251[j] = 0;
	return true;
}

void Encoding::convert_windows1251_to_utf8(char* windows1251, const char* utf8, unsigned int len)
{
	static const int table[128] = 
    {                    
        // 80
        0x82D0,     0x83D0,     0x9A80E2,   0x93D1,     0x9E80E2,   0xA680E2,   0xA080E2,   0xA180E2,
        0xAC82E2,   0xB080E2,   0x89D0,     0xB980E2,   0x8AD0,     0x8CD0,     0x8BD0,     0x8FD0,
        // 90
        0x92D1,     0x9880E2,   0x9980E2,   0x9C80E2,   0x9D80E2,   0xA280E2,   0x9380E2,   0x9480E2,
        0,          0xA284E2,   0x99D1,     0xBA80E2,   0x9AD1,     0x9CD1,     0x9BD1,     0x9FD1,
        // A0
        0xA0C2,     0x8ED0,     0x9ED1,     0x88D0,     0xA4C2,     0x90D2,     0xA6C2,     0xA7C2,              
        0x81D0,     0xA9C2,     0x84D0,     0xABC2,     0xACC2,     0xADC2,     0xAEC2,     0x87D0,
        // B0
        0xB0C2,     0xB1C2,     0x86D0,     0x96D1,     0x91D2,     0xB5C2,     0xB6C2,     0xB7C2,              
        0x91D1,     0x9684E2,   0x94D1,     0xBBC2,     0x98D1,     0x85D0,     0x95D1,     0x97D1,
        // C0
        0x90D0,     0x91D0,     0x92D0,     0x93D0,     0x94D0,     0x95D0,     0x96D0,     0x97D0,
        0x98D0,     0x99D0,     0x9AD0,     0x9BD0,     0x9CD0,     0x9DD0,     0x9ED0,     0x9FD0,
        // D0
        0xA0D0,     0xA1D0,     0xA2D0,     0xA3D0,     0xA4D0,     0xA5D0,     0xA6D0,     0xA7D0,
        0xA8D0,     0xA9D0,     0xAAD0,     0xABD0,     0xACD0,     0xADD0,     0xAED0,     0xAFD0,
        // E0
        0xB0D0,     0xB1D0,     0xB2D0,     0xB3D0,     0xB4D0,     0xB5D0,     0xB6D0,     0xB7D0,
        0xB8D0,     0xB9D0,     0xBAD0,     0xBBD0,     0xBCD0,     0xBDD0,     0xBED0,     0xBFD0,
        // F0
        0x80D1,     0x81D1,     0x82D1,     0x83D1,     0x84D1,     0x85D1,     0x86D1,     0x87D1,
        0x88D1,     0x89D1,     0x8AD1,     0x8BD1,     0x8CD1,     0x8DD1,     0x8ED1,     0x8FD1
    };

	int count = 0;

	while (*utf8)
	{
		if(len && (count >= len)) break;

		if (*utf8 & 0x80)
		{
			int v = table[(int)(0x7f & *utf8++)];
			if (!v) continue;
			*windows1251++ = (char)v;
			*windows1251++ = (char)(v >> 8);
			if (v >>= 16) {
				*windows1251++ = (char)v;
			}
		}
		else
		{
			*windows1251++ = *utf8++;
		}

		count++;
	}

	*windows1251 = 0;
}