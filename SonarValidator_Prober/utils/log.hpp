#ifndef SONAR_UTILS_LOG_HPP
#define SONAR_UTILS_LOG_HPP

#include <algorithm>
#include <chrono>
#include <cctype>
#include <cstdlib>
#include <ctime>
#include <iomanip>
#include <iostream>
#include <mutex>
#include <sstream>
#include <string>
#include <string_view>
#include <unordered_map>

namespace sonar::logging {
enum class Level { Debug, Info, Warn, Error, Off };

inline Level ParseLevel(std::string value) {
    for (char& c : value) c = static_cast<char>(std::toupper(static_cast<unsigned char>(c)));
    if (value == "DEBUG") return Level::Debug;
    if (value == "WARN" || value == "WARNING") return Level::Warn;
    if (value == "ERROR") return Level::Error;
    if (value == "OFF") return Level::Off;
    return Level::Info;
}

inline Level ConfiguredLevel() {
    static const Level value = ParseLevel(std::getenv("SONAR_LOG_LEVEL") ? std::getenv("SONAR_LOG_LEVEL") : "INFO");
    return value;
}
inline bool Enabled(Level level) { return level >= ConfiguredLevel() && level != Level::Off; }

inline std::string SingleLine(std::string_view input, std::size_t limit) {
    std::string text;
    text.reserve(std::min(input.size(), limit + 1));
    for (unsigned char c : input) {
        if (text.size() > limit) break;
        if (c < 32 || c == 127) c = ' ';
        if (c == ' ' && (text.empty() || text.back() == ' ')) continue;
        text.push_back(static_cast<char>(c));
    }
    if (text.size() > limit) {
        std::size_t end = limit;
        while (end && (static_cast<unsigned char>(text[end]) & 0xc0) == 0x80) --end;
        text.resize(end);
        text += "...";
    }
    while (!text.empty() && text.back() == ' ') text.pop_back();
    return text;
}

class RepeatGate {
    struct Previous { std::string message; std::chrono::steady_clock::time_point emitted; unsigned skipped = 0; };
    std::unordered_map<std::string, Previous> previous_;
public:
    struct Decision { bool emit; unsigned skipped; };
    Decision Check(const std::string& site, const std::string& message,
                   std::chrono::steady_clock::time_point now) {
        auto found = previous_.find(site);
        if (found != previous_.end()) {
            auto& last = found->second;
            if (last.message == message && now - last.emitted < std::chrono::minutes(1)) {
                ++last.skipped;
                return {false, 0};
            }
            unsigned skipped = last.message == message ? last.skipped : 0;
            last = {message, now, 0};
            return {true, skipped};
        }
        if (previous_.size() >= 512) previous_.clear();
        previous_.emplace(site, Previous{message, now, 0});
        return {true, 0};
    }
};

inline void Write(Level level, const char* file, int line, const std::string& raw) {
    static std::mutex mutex;
    static RepeatGate repeated;
    const std::string message = SingleLine(raw, level == Level::Debug ? 1024 : 320);
    if (message.empty()) return;
    std::lock_guard lock(mutex);
    unsigned skipped = 0;
    if (level == Level::Warn) {
        auto decision = repeated.Check(std::string(file) + ':' + std::to_string(line), message,
                                       std::chrono::steady_clock::now());
        if (!decision.emit) return;
        skipped = decision.skipped;
    }
    const auto now = std::chrono::system_clock::to_time_t(std::chrono::system_clock::now());
    std::tm local{};
    localtime_r(&now, &local);
    const char* label = level == Level::Debug ? "DEBUG" : level == Level::Info ? "INFO" :
                        level == Level::Warn ? "WARN" : "ERROR";
    std::cerr << std::put_time(&local, "%Y-%m-%d %H:%M:%S%z") << ' ' << label << ' ' << message;
    if (skipped) std::cerr << " (반복 " << skipped << "회 생략)";
    std::cerr << '\n';
}

class Line {
    Level level_;
    const char* file_;
    int line_;
    std::ostringstream text_;
public:
    Line(Level level, const char* file, int line) : level_(level), file_(file), line_(line) {}
    ~Line() noexcept { try { Write(level_, file_, line_, text_.str()); } catch (...) {} }
    template<class T> Line& operator<<(const T& value) { text_ << value; return *this; }
    Line& operator<<(std::ostream& (*value)(std::ostream&)) { value(text_); return *this; }
};
}

#define SV_LOG_AT(level) for (bool sv_log_once = ::sonar::logging::Enabled(level); sv_log_once; sv_log_once = false) ::sonar::logging::Line(level, __FILE__, __LINE__)
#define SV_LOG_DEBUG SV_LOG_AT(::sonar::logging::Level::Debug)
#define SV_LOG_INFO SV_LOG_AT(::sonar::logging::Level::Info)
#define SV_LOG_WARN SV_LOG_AT(::sonar::logging::Level::Warn)
#define SV_LOG_ERROR SV_LOG_AT(::sonar::logging::Level::Error)
#endif
