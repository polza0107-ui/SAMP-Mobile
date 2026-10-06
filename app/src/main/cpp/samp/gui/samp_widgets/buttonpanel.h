#pragma once

class ButtonPanel : public Layout
{
public:
	ButtonPanel();

	Button* m_bY;
	Button* m_bN;
	Button* m_bWheel;
};