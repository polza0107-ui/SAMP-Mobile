#pragma once
#include "../../game/game.h"
#include "../../game/util.h"

#include "../../main.h"

#include "../../settings.h"
#include "util/CUtil.h"


extern CSettings* pSettings;

class VoiceButton : public Button
{
public:
	VoiceButton() : Button("TALK", UISettings::fontSize() / 2) {
		m_recording = false;
		/* 5:3 aspect ratio */
		m_texture_micro_on = (RwTexture*)CUtil::LoadTextureFromDB("samp", "voiceactive");
		if (!m_texture_micro_on) m_texture_micro_on = (RwTexture*)CUtil::LoadTextureFromDB("samp", "icon_micro_on");
		if (!m_texture_micro_on) m_texture_micro_on = (RwTexture*)CUtil::LoadTextureFromDB("samp", "voice_on");

		m_texture_micro_off = (RwTexture*)CUtil::LoadTextureFromDB("samp", "voicepassive");
		if (!m_texture_micro_off) m_texture_micro_off = (RwTexture*)CUtil::LoadTextureFromDB("samp", "icon_micro_off");
		if (!m_texture_micro_off) m_texture_micro_off = (RwTexture*)CUtil::LoadTextureFromDB("samp", "voice_off");
	}

	virtual void draw(ImGuiRenderer* renderer) override
	{
		if(!pSettings->Get().bVoiceChatEnable) return;

		if (countdown > 0 && recording() == 1) countdown--;
		if (countdown == 0 && recording() == 1) setRecording(0);

		RwRaster* raster = nullptr;
		if (recording() && m_texture_micro_on) raster = m_texture_micro_on->raster;
		else if (!recording() && m_texture_micro_off) raster = m_texture_micro_off->raster;

		ImDrawList* drawList = ImGui::GetBackgroundDrawList();
		ImVec2 center = absolutePosition() + size() * 0.5f;
		float radius = (size().x < size().y ? size().x : size().y) * 0.44f;

		if (recording()) {
			// Active transmitting: Glowing Gold / Crimson pulsing circle
			drawList->AddCircleFilled(center, radius + 4.0f, ImColor(229, 169, 60, 60), 32);
			drawList->AddCircleFilled(center, radius, ImColor(229, 70, 40, 220), 32);
			drawList->AddCircle(center, radius, ImColor(255, 215, 0, 255), 32, 2.5f);
		} else {
			// Idle listening: Sleek frosted dark disc with gold-tinted rim
			drawList->AddCircleFilled(center, radius, ImColor(16, 20, 26, 175), 32);
			drawList->AddCircle(center, radius, ImColor(229, 169, 60, 140), 32, 1.5f);
		}

		if (raster) {
			ImVec2 iconPad = ImVec2(radius * 0.55f, radius * 0.55f);
			renderer->drawImage(center - iconPad, center + iconPad, raster);
		} else {
			// Render vector microphone shape if texture raster isn't loaded
			float r = radius * 0.45f;
			ImColor micColor = recording() ? ImColor(255, 255, 255, 255) : ImColor(229, 169, 60, 230);
			// Mic capsule
			drawList->AddRectFilled(ImVec2(center.x - r * 0.4f, center.y - r * 0.8f), ImVec2(center.x + r * 0.4f, center.y + r * 0.2f), micColor, r * 0.4f);
			// Mic base arc & stem
			drawList->AddCircle(center, r * 0.65f, micColor, 16, 1.8f);
			drawList->AddLine(ImVec2(center.x, center.y + r * 0.65f), ImVec2(center.x, center.y + r * 1.0f), micColor, 2.0f);
			drawList->AddLine(ImVec2(center.x - r * 0.5f, center.y + r * 1.0f), ImVec2(center.x + r * 0.5f, center.y + r * 1.0f), micColor, 2.0f);
		}
	}

	void touchPopEvent() override
	{
		countdown = 500;
		setRecording(0);
	}

	void touchPushEvent() override
	{
			//setRecording(recording() ^ 1);
		setRecording(1);
		countdown = 500;
	}

	void setRecording(bool recording) 
	{ 
		if (recording == 1) countdown = 200;
		m_recording = recording;
		this->setCaptionColor(m_recording ? ImColor(1.0f, 0.0f, 0.0f) : ImColor(1.0f, 1.0f, 1.0f));
	}

	bool recording() const { return m_recording; }

private:
	bool m_recording;
	RwTexture* m_texture_micro_on;
	RwTexture* m_texture_micro_off;
	//int countdown = 1000;
public:
	int countdown = 1000;
};