#include "../../main.h"
#include "..//gui.h"
#include "button.h"

extern bool OpenButton;

Button::Button(const std::string& caption, float font_size)
{
	m_callback = nullptr;

	m_label = new Label(caption, ImColor(1.0f, 1.0f, 1.0f), false, font_size);
	this->addChild(m_label);

	m_color = UISettings::buttonColor();
	m_colorFocused = UISettings::buttonFocusedColor();
}

void Button::performLayout()
{
	float padding = UISettings::padding();

	m_label->performLayout();
	this->setSize(m_label->size() + ImVec2(padding * 2, padding / 2 * 2));

	m_label->setPosition((size() - m_label->size()) / 2);
}

void Button::draw(ImGuiRenderer* renderer)
{
	ImDrawList* drawList = ImGui::GetBackgroundDrawList();
	ImVec2 pMin = absolutePosition();
	ImVec2 pMax = absolutePosition() + size();
	float radius = 10.0f;

	// Dark semi-transparent background (#111827 style like APK notifications)
	ImColor fillCol = focused() ? ImColor(229, 169, 60, 220) : ImColor(17, 24, 39, 210);
	drawList->AddRectFilled(pMin, pMax, fillCol, radius);

	// Sleek gold outline border (1.5dp)
	ImColor borderCol = focused() ? ImColor(255, 230, 130, 255) : ImColor(229, 169, 60, 175);
	drawList->AddRect(pMin, pMax, borderCol, radius, 15, 1.5f);

	Widget::draw(renderer);
}

void Button::touchPopEvent()
{
	if (m_callback) m_callback();
}


//============== Custom Button=========================//
CButton::CButton(const std::string& caption, float font_size)
{
	m_callback = nullptr;

	m_label = new Label(caption, ImColor(1.0f, 1.0f, 1.0f), false, font_size);
	this->addChild(m_label);

	m_color = UISettings::buttonColor();
	m_colorFocused = UISettings::buttonFocusedColor();
}

void CButton::performLayout()
{
	float padding = UISettings::padding();

	m_label->performLayout();
	this->setSize(m_label->size() + ImVec2(padding * 2, padding / 2 * 2));

	m_label->setPosition((size() - m_label->size()) / 2);
}

void CButton::draw(ImGuiRenderer* renderer)
{
	ImDrawList* drawList = ImGui::GetBackgroundDrawList();
	ImVec2 pMin = absolutePosition();
	ImVec2 pMax = absolutePosition() + size();
	float radius = 10.0f;

	// Notification-style dark glass background
	ImColor fillCol = focused() ? ImColor(229, 169, 60, 220) : ImColor(17, 24, 39, 210);
	drawList->AddRectFilled(pMin, pMax, fillCol, radius);

	// Gold accent outline
	ImColor borderCol = focused() ? ImColor(255, 230, 130, 255) : ImColor(229, 169, 60, 175);
	drawList->AddRect(pMin, pMax, borderCol, radius, 15, 1.5f);

	Widget::draw(renderer);
}

void CButton::touchPopEvent()
{
	if (m_callback) m_callback();
}
//=======================================//

//======== >> Button ====================//
OButton::OButton(const std::string& caption, float font_size)
{
	m_callback = nullptr;

	m_label = new Label(caption, ImColor(1.0f, 1.0f, 1.0f), false, font_size);
	this->addChild(m_label);

	m_color = UISettings::buttonColor();
	m_colorFocused = UISettings::buttonFocusedColor();
}

void OButton::performLayout()
{
	float padding = UISettings::padding();

	m_label->performLayout();
	this->setSize(m_label->size() + ImVec2(padding * 2, padding / 2 * 2));

	m_label->setPosition((size() - m_label->size()) / 2);
}

void OButton::draw(ImGuiRenderer* renderer)
{
	ImDrawList* drawList = ImGui::GetBackgroundDrawList();
	ImVec2 pMin = absolutePosition();
	ImVec2 pMax = absolutePosition() + size();
	float radius = 10.0f;

	ImColor fillCol = focused() ? ImColor(229, 169, 60, 220) : ImColor(17, 24, 39, 210);
	drawList->AddRectFilled(pMin, pMax, fillCol, radius);

	ImColor borderCol = focused() ? ImColor(255, 230, 130, 255) : ImColor(229, 169, 60, 175);
	drawList->AddRect(pMin, pMax, borderCol, radius, 15, 1.5f);

	Widget::draw(renderer);
}

void OButton::touchPopEvent()
{
	if (m_callback) m_callback();
}
//======================================//